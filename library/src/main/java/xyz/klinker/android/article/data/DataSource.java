package xyz.klinker.android.article.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.concurrent.atomic.AtomicInteger;

import xyz.klinker.android.article.ArticleUtils;
import xyz.klinker.android.article.data.model.ArticleModel;
import xyz.klinker.android.article.data.model.ContentModel;

/**
 * Handles interactions with database models.
 */
public class DataSource {

    private static final String TAG = "DataSource";
    private static volatile DataSource instance;

    protected Context context;
    private SQLiteDatabase database;
    private DatabaseSQLiteHelper dbHelper;
    private AtomicInteger openCounter = new AtomicInteger();

    /**
     * Gets a new instance of the DataSource.
     *
     * @param context the current application instance.
     * @return the data source.
     */
    public static DataSource getInstance(Context context) {
        if (instance == null) {
            instance = new DataSource(context);
        }

        return instance;
    }

    /**
     * Private constructor to force a singleton.
     *
     * @param context Current calling context
     */
    private DataSource(Context context) {
        this.context = context;
        this.dbHelper = new DatabaseSQLiteHelper(context);
    }

    /**
     * Opens the database.
     */
    public synchronized void open() {
        if (openCounter.incrementAndGet() == 1) {
            database = dbHelper.getWritableDatabase();
        }
    }

    /**
     * Checks if the database is open.
     */
    public boolean isOpen() {
        return database != null && database.isOpen();
    }

    /**
     * Closes the database.
     */
    public synchronized void close() {
        if (openCounter.decrementAndGet() == 0) {
            dbHelper.close();
        }
    }

    /**
     * Deletes all data from the tables.
     */
    public void clearTables() {
        database.delete(ContentModel.TABLE, null, null);
        database.delete(ArticleModel.TABLE, null, null);
    }

    /**
     * Begins a bulk transaction on the database.
     */
    public void beginTransaction() {
        database.beginTransaction();
    }

    /**
     * Executes a raw sql statement on the database. Can be used in conjunction with
     * beginTransaction and endTransaction if bulk.
     *
     * @param sql the sql statement.
     */
    public void execSql(String sql) {
        database.execSQL(sql);
    }

    /**
     * Execute a raw sql query on the database.
     *
     * @param sql the sql statement
     * @return cursor for the data
     */
    public Cursor rawQuery(String sql) {
        return database.rawQuery(sql, null);
    }

    /**
     * Sets the transaction into a successful state so that it can be committed to the database.
     * Should be used in conjunction with beginTransaction() and endTransaction().
     */
    public void setTransactionSuccessful() {
        database.setTransactionSuccessful();
    }

    /**
     * Ends a bulk transaction on the database.
     */
    public void endTransaction() {
        database.endTransaction();
    }

    /**
     * Inserts a single article into the database for caching purposes.
     */
    public void insertArticle(Article article) {
        // remove any extra query parameters from the url
        article.url = ArticleUtils.removeUrlParameters(article.url);

        ContentValues values = new ContentValues(10);
        values.put(ArticleModel.COLUMN_ALIAS, article.alias);
        values.put(ArticleModel.COLUMN_URL, article.url);
        values.put(ArticleModel.COLUMN_TITLE, article.title);
        values.put(ArticleModel.COLUMN_DESCRIPTION, article.description);
        values.put(ArticleModel.COLUMN_IMAGE, article.image);
        values.put(ArticleModel.COLUMN_AUTHOR, article.author);
        values.put(ArticleModel.COLUMN_SOURCE, article.source);
        values.put(ArticleModel.COLUMN_DOMAIN, article.domain);
        values.put(ArticleModel.COLUMN_DURATION, article.duration);
        values.put(ArticleModel.COLUMN_INSERTED_AT, System.currentTimeMillis());
        values.put(ArticleModel.COLUMN_IS_ARTICLE, article.isArticle);

        long id = database.insert(ArticleModel.TABLE, null, values);

        values = new ContentValues(2);
        values.put(ContentModel.COLUMN_ARTICLE_ID, id);
        values.put(ContentModel.COLUMN_CONTENT, article.content);

        database.insert(ContentModel.TABLE, null, values);
    }

    /**
     * Gets a single article from the database. If there are multiple with the same URL, only the
     * first is returned.
     */
    public Article getArticle(String url) {
        // remove any extra query parameters from the url
        url = ArticleUtils.removeUrlParameters(url);

        Cursor cursor = database.query(
                ArticleModel.TABLE + " a left outer join " + ContentModel.TABLE + " c " +
                        "on a." + ArticleModel.COLUMN_ID + " = c." + ContentModel.COLUMN_ARTICLE_ID,
                new String[] {
                        "a." + ArticleModel.COLUMN_ID + " as " + ArticleModel.COLUMN_ID,
                        "a." + ArticleModel.COLUMN_ALIAS + " as " + ArticleModel.COLUMN_ALIAS,
                        "a." + ArticleModel.COLUMN_URL + " as " + ArticleModel.COLUMN_URL,
                        "a." + ArticleModel.COLUMN_TITLE + " as " + ArticleModel.COLUMN_TITLE,
                        "a." + ArticleModel.COLUMN_DESCRIPTION + " as " + ArticleModel.COLUMN_DESCRIPTION,
                        "a." + ArticleModel.COLUMN_IMAGE + " as " + ArticleModel.COLUMN_IMAGE,
                        "a." + ArticleModel.COLUMN_AUTHOR + " as " + ArticleModel.COLUMN_AUTHOR,
                        "a." + ArticleModel.COLUMN_SOURCE + " as " + ArticleModel.COLUMN_SOURCE,
                        "a." + ArticleModel.COLUMN_DOMAIN + " as " + ArticleModel.COLUMN_DOMAIN,
                        "a." + ArticleModel.COLUMN_DURATION + " as " + ArticleModel.COLUMN_DURATION,
                        "a." + ArticleModel.COLUMN_INSERTED_AT + " as " + ArticleModel.COLUMN_INSERTED_AT,
                        "a." + ArticleModel.COLUMN_IS_ARTICLE + " as " + ArticleModel.COLUMN_IS_ARTICLE,
                        "c." + ContentModel.COLUMN_CONTENT + " as " + ContentModel.COLUMN_CONTENT
                },
                ArticleModel.COLUMN_URL + "=?",
                new String[] { url },
                null,
                null,
                null);

        if (cursor != null && cursor.moveToFirst()) {
            Article article = new Article(cursor);
            cursor.close();
            return article;
        } else {
            return null;
        }
    }

    /**
     * Gets all articles in the database.
     *
     * NOTE: this method does not return the content associated with the article, that would be
     *       slow as some articles can get very large.
     */
    public Cursor getAllArticles() {
        return database.query(
                ArticleModel.TABLE,
                null,
                null,
                null,
                null,
                null,
                ArticleModel.COLUMN_INSERTED_AT + " desc");
    }

}
