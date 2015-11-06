package eu.kanade.mangafeed.data.helpers;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import eu.kanade.mangafeed.data.models.Chapter;
import eu.kanade.mangafeed.data.models.Download;
import eu.kanade.mangafeed.data.models.DownloadQueue;
import eu.kanade.mangafeed.data.models.Manga;
import eu.kanade.mangafeed.data.models.Page;
import eu.kanade.mangafeed.events.DownloadChaptersEvent;
import eu.kanade.mangafeed.sources.base.Source;
import eu.kanade.mangafeed.util.DiskUtils;
import eu.kanade.mangafeed.util.DynamicConcurrentMergeOperator;
import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import timber.log.Timber;

public class DownloadManager {

    private Context context;
    private SourceManager sourceManager;
    private PreferencesHelper preferences;
    private Gson gson;

    private PublishSubject<Download> downloadsQueueSubject;
    private BehaviorSubject<Integer> threadsNumber;
    private Subscription downloadsSubscription;
    private Subscription threadNumberSubscription;

    private DownloadQueue queue;
    private transient boolean isQueuePaused;

    public static final String PAGE_LIST_FILE = "index.json";

    public DownloadManager(Context context, SourceManager sourceManager, PreferencesHelper preferences) {
        this.context = context;
        this.sourceManager = sourceManager;
        this.preferences = preferences;
        this.gson = new Gson();

        queue = new DownloadQueue();

        initializeDownloadsSubscription();
    }

    private void initializeDownloadsSubscription() {
        if (downloadsSubscription != null && !downloadsSubscription.isUnsubscribed())
            downloadsSubscription.unsubscribe();

        if (threadNumberSubscription != null && !threadNumberSubscription.isUnsubscribed())
            threadNumberSubscription.unsubscribe();

        downloadsQueueSubject = PublishSubject.create();
        threadsNumber = BehaviorSubject.create();

        threadNumberSubscription = preferences.getDownloadTheadsObservable()
                .filter(n -> !isQueuePaused)
                .doOnNext(n -> isQueuePaused = (n == 0))
                .subscribe(threadsNumber::onNext);

        downloadsSubscription = downloadsQueueSubject
                .observeOn(Schedulers.newThread())
                .lift(new DynamicConcurrentMergeOperator<>(this::downloadChapter, threadsNumber))
                .onBackpressureBuffer()
                .subscribe(page -> {},
                        e -> Timber.e(e.fillInStackTrace(), e.getMessage()));
    }

    // Create a download object for every chapter in the event and add them to the downloads queue
    public void onDownloadChaptersEvent(DownloadChaptersEvent event) {
        final Manga manga = event.getManga();
        final Source source = sourceManager.get(manga.source);

        for (Chapter chapter : event.getChapters()) {
            Download download = new Download(source, manga, chapter);

            if (!isChapterDownloaded(download)) {
                queue.add(download);
                downloadsQueueSubject.onNext(download);
            }
        }
    }

    // Check if a chapter is already downloaded
    private boolean isChapterDownloaded(Download download) {
        // If the chapter is already queued, don't add it again
        for (Download queuedDownload : queue.get()) {
            if (download.chapter.id.equals(queuedDownload.chapter.id))
                return true;
        }

        // Add the directory to the download object for future access
        download.directory = getAbsoluteChapterDirectory(download);

        // If the directory doesn't exist, the chapter isn't downloaded.
        if (!download.directory.exists()) {
            return false;
        }

        // If the page list doesn't exist, the chapter isn't download (or maybe it's,
        // but we consider it's not)
        List<Page> savedPages = getSavedPageList(download);
        if (savedPages == null)
            return false;

        // Add the page list to the download object for future access
        download.pages = savedPages;

        // If the number of files matches the number of pages, the chapter is downloaded.
        // We have the index file, so we check one file more
        return savedPages.size() + 1 == download.directory.listFiles().length;
    }

    // Download the entire chapter
    private Observable<Page> downloadChapter(Download download) {
        try {
            DiskUtils.createDirectory(download.directory);
        } catch (IOException e) {
            Timber.e(e.getMessage());
        }

        Observable<List<Page>> pageListObservable = download.pages == null ?
                // Pull page list from network and add them to download object
                download.source
                        .pullPageListFromNetwork(download.chapter.url)
                        .doOnNext(pages -> download.pages = pages)
                        .doOnNext(pages -> savePageList(download)) :
                // Or if the file exists, start from here
                Observable.just(download.pages);

        return pageListObservable
                .doOnNext(pages -> download.setStatus(Download.DOWNLOADING))
                // Get all the URLs to the source images, fetch pages if necessary
                .flatMap(pageList -> Observable.merge(
                        Observable.from(pageList).filter(page -> page.getImageUrl() != null),
                        download.source.getRemainingImageUrlsFromPageList(pageList)))
                // Start downloading images, consider we can have downloaded images already
                .concatMap(page -> getDownloadedImage(page, download.source, download.directory))
                // Do after download completes
                .doOnCompleted(() -> onDownloadCompleted(download));
    }

    // Get downloaded image if exists, otherwise download it with the method below
    public Observable<Page> getDownloadedImage(final Page page, Source source, File chapterDir) {
        Observable<Page> pageObservable = Observable.just(page);
        if (page.getImageUrl() == null)
            return pageObservable;

        String imageFilename = getImageFilename(page);
        File imagePath = new File(chapterDir, imageFilename);

        if (!isImageDownloaded(imagePath)) {
            page.setStatus(Page.DOWNLOAD_IMAGE);
            pageObservable = downloadImage(page, source, chapterDir, imageFilename);
        }

        return pageObservable
                .doOnNext(p -> p.setImagePath(imagePath.getAbsolutePath()))
                .doOnNext(p -> p.setStatus(Page.READY))
                .doOnError(e -> page.setStatus(Page.ERROR))
                // Allow to download the remaining images
                .onErrorResumeNext(e -> Observable.just(page));
    }

    // Download the image
    private Observable<Page> downloadImage(final Page page, Source source, File chapterDir, String imageFilename) {
        return source.getImageProgressResponse(page)
                .flatMap(resp -> {
                    try {
                        DiskUtils.saveBufferedSourceToDirectory(resp.body().source(), chapterDir, imageFilename);
                    } catch (IOException e) {
                        Timber.e(e.fillInStackTrace(), e.getMessage());
                        throw new IllegalStateException("Unable to save image");
                    }
                    return Observable.just(page);
                });
    }

    // Get the filename for an image given the page
    private String getImageFilename(Page page) {
        return page.getImageUrl().substring(
                page.getImageUrl().lastIndexOf("/") + 1,
                page.getImageUrl().length());
    }

    private boolean isImageDownloaded(File imagePath) {
        return imagePath.exists();
    }

    // Called when a download finishes. This doesn't mean the download was successful, so we check it
    private void onDownloadCompleted(final Download download) {
        checkDownloadIsSuccessful(download);
        savePageList(download);
    }

    private void checkDownloadIsSuccessful(final Download download) {
        int expectedProgress = download.pages.size() * 100;
        int actualProgress = 0;
        int status = Download.DOWNLOADED;
        // If any page has an error, the download result will be error
        for (Page page : download.pages) {
            actualProgress += page.getProgress();
            if (page.getStatus() == Page.ERROR) status = Download.ERROR;
        }
        // If the download is successful, it's safer to use the expected progress
        download.totalProgress = (status == Download.DOWNLOADED) ? expectedProgress : actualProgress;
        download.setStatus(status);
    }

    // Return the page list from the chapter's directory if it exists, null otherwise
    public List<Page> getSavedPageList(Source source, Manga manga, Chapter chapter) {
        List<Page> pages = null;
        File chapterDir = getAbsoluteChapterDirectory(source, manga, chapter);
        File pagesFile = new File(chapterDir, PAGE_LIST_FILE);

        try {
            if (pagesFile.exists()) {
                JsonReader reader = new JsonReader(new FileReader(pagesFile.getAbsolutePath()));
                Type collectionType = new TypeToken<List<Page>>() {}.getType();
                pages = gson.fromJson(reader, collectionType);
                reader.close();
            }
        } catch (Exception e) {
            Timber.e(e.fillInStackTrace(), e.getMessage());
        }
        return pages;
    }

    // Shortcut for the method above
    private List<Page> getSavedPageList(Download download) {
        return getSavedPageList(download.source, download.manga, download.chapter);
    }

    // Save the page list to the chapter's directory
    public void savePageList(Source source, Manga manga, Chapter chapter, List<Page> pages) {
        File chapterDir = getAbsoluteChapterDirectory(source, manga, chapter);
        File pagesFile = new File(chapterDir, PAGE_LIST_FILE);

        FileOutputStream out;
        try {
            out = new FileOutputStream(pagesFile);
            out.write(gson.toJson(pages).getBytes());
            out.flush();
            out.close();
        } catch (Exception e) {
            Timber.e(e.fillInStackTrace(), e.getMessage());
        }
    }

    // Shortcut for the method above
    private void savePageList(Download download) {
        savePageList(download.source, download.manga, download.chapter, download.pages);
    }

    // Get the absolute path to the chapter directory
    public File getAbsoluteChapterDirectory(Source source, Manga manga, Chapter chapter) {
        String chapterRelativePath = source.getName() +
                File.separator +
                manga.title.replaceAll("[^a-zA-Z0-9.-]", "_") +
                File.separator +
                chapter.name.replaceAll("[^a-zA-Z0-9.-]", "_");

        return new File(preferences.getDownloadsDirectory(), chapterRelativePath);
    }

    // Shortcut for the method above
    private File getAbsoluteChapterDirectory(Download download) {
        return getAbsoluteChapterDirectory(download.source, download.manga, download.chapter);
    }

    public void deleteChapter(Source source, Manga manga, Chapter chapter) {
        File path = getAbsoluteChapterDirectory(source, manga, chapter);
        DiskUtils.deleteFiles(path);
    }

    public DownloadQueue getQueue() {
        return queue;
    }

    public void pauseDownloads() {
        threadsNumber.onNext(0);
    }

    public void resumeDownloads() {
        isQueuePaused = false;
        threadsNumber.onNext(preferences.getDownloadThreads());
    }
}