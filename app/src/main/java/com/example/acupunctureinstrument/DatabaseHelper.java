package com.example.acupunctureinstrument;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 数据库助手类
 * 文件名：DatabaseHelper.java
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "acupuncture_db";
    private static final int DATABASE_VERSION = 2; // 升级版本号

    // 患者表
    public static final String TABLE_PATIENTS = "patients";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_PATIENT_ID = "patient_id";
    public static final String COLUMN_PATIENT_NAME = "patient_name";
    public static final String COLUMN_AGE = "age";
    public static final String COLUMN_GENDER = "gender";
    public static final String COLUMN_TREATMENT_CONTENT = "treatment_content";
    public static final String COLUMN_REMARK = "remark";
    public static final String COLUMN_FREQUENCY = "frequency";
    public static final String COLUMN_CHANNEL_INTENSITIES = "channel_intensities";
    public static final String COLUMN_CREATE_TIME = "create_time";
    public static final String COLUMN_UPDATE_TIME = "update_time";

    // 创建患者表的SQL语句
    private static final String CREATE_PATIENTS_TABLE =
            "CREATE TABLE " + TABLE_PATIENTS + "(" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_PATIENT_ID + " TEXT NOT NULL, " +
                    COLUMN_PATIENT_NAME + " TEXT NOT NULL, " +
                    COLUMN_AGE + " INTEGER DEFAULT 0, " +
                    COLUMN_GENDER + " TEXT DEFAULT '', " +
                    COLUMN_TREATMENT_CONTENT + " TEXT DEFAULT '', " +
                    COLUMN_REMARK + " TEXT DEFAULT '', " +
                    COLUMN_FREQUENCY + " INTEGER NOT NULL, " +
                    COLUMN_CHANNEL_INTENSITIES + " TEXT NOT NULL, " +
                    COLUMN_CREATE_TIME + " INTEGER NOT NULL, " +
                    COLUMN_UPDATE_TIME + " INTEGER NOT NULL" +
                    ")";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_PATIENTS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // 添加新列到现有表
            db.execSQL("ALTER TABLE " + TABLE_PATIENTS + " ADD COLUMN " + COLUMN_AGE + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_PATIENTS + " ADD COLUMN " + COLUMN_GENDER + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE_PATIENTS + " ADD COLUMN " + COLUMN_TREATMENT_CONTENT + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE_PATIENTS + " ADD COLUMN " + COLUMN_REMARK + " TEXT DEFAULT ''");
        }
    }
}