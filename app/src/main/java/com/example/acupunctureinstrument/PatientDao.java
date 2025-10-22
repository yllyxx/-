package com.example.acupunctureinstrument;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/**
 * 患者数据访问对象
 * 文件名：PatientDao.java
 */
public class PatientDao {
    private DatabaseHelper dbHelper;

    public PatientDao(Context context) {
        dbHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * 插入患者数据
     */
    public long insertPatient(Patient patient) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(DatabaseHelper.COLUMN_PATIENT_ID, patient.getPatientId());
        values.put(DatabaseHelper.COLUMN_PATIENT_NAME, patient.getPatientName());
        values.put(DatabaseHelper.COLUMN_AGE, patient.getAge());
        values.put(DatabaseHelper.COLUMN_GENDER, patient.getGender());
        values.put(DatabaseHelper.COLUMN_TREATMENT_CONTENT, patient.getTreatmentContent());
        values.put(DatabaseHelper.COLUMN_REMARK, patient.getRemark());
        values.put(DatabaseHelper.COLUMN_FREQUENCY, patient.getFrequency());
        values.put(DatabaseHelper.COLUMN_CHANNEL_INTENSITIES, patient.getChannelIntensities());
        values.put(DatabaseHelper.COLUMN_CREATE_TIME, patient.getCreateTime());
        values.put(DatabaseHelper.COLUMN_UPDATE_TIME, patient.getUpdateTime());

        long id = db.insert(DatabaseHelper.TABLE_PATIENTS, null, values);
        db.close();
        return id;
    }

    /**
     * 更新患者数据
     */
    public int updatePatient(Patient patient) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(DatabaseHelper.COLUMN_PATIENT_ID, patient.getPatientId());
        values.put(DatabaseHelper.COLUMN_PATIENT_NAME, patient.getPatientName());
        values.put(DatabaseHelper.COLUMN_AGE, patient.getAge());
        values.put(DatabaseHelper.COLUMN_GENDER, patient.getGender());
        values.put(DatabaseHelper.COLUMN_TREATMENT_CONTENT, patient.getTreatmentContent());
        values.put(DatabaseHelper.COLUMN_REMARK, patient.getRemark());
        values.put(DatabaseHelper.COLUMN_FREQUENCY, patient.getFrequency());
        values.put(DatabaseHelper.COLUMN_CHANNEL_INTENSITIES, patient.getChannelIntensities());
        values.put(DatabaseHelper.COLUMN_UPDATE_TIME, System.currentTimeMillis());

        int rowsAffected = db.update(DatabaseHelper.TABLE_PATIENTS, values,
                DatabaseHelper.COLUMN_ID + "=?", new String[]{String.valueOf(patient.getId())});
        db.close();
        return rowsAffected;
    }

    /**
     * 删除患者数据
     */
    public int deletePatient(int id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rowsAffected = db.delete(DatabaseHelper.TABLE_PATIENTS,
                DatabaseHelper.COLUMN_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return rowsAffected;
    }

    /**
     * 根据ID获取患者数据
     */
    public Patient getPatientById(int id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.TABLE_PATIENTS, null,
                DatabaseHelper.COLUMN_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);

        Patient patient = null;
        if (cursor.moveToFirst()) {
            patient = createPatientFromCursor(cursor);
        }
        cursor.close();
        db.close();
        return patient;
    }

    /**
     * 根据患者ID获取患者数据
     */
    public Patient getPatientByPatientId(String patientId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.TABLE_PATIENTS, null,
                DatabaseHelper.COLUMN_PATIENT_ID + "=?", new String[]{patientId},
                null, null, null);

        Patient patient = null;
        if (cursor.moveToFirst()) {
            patient = createPatientFromCursor(cursor);
        }
        cursor.close();
        db.close();
        return patient;
    }

    /**
     * 获取所有患者数据
     */
    public List<Patient> getAllPatients() {
        List<Patient> patients = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.TABLE_PATIENTS, null, null, null,
                null, null, DatabaseHelper.COLUMN_UPDATE_TIME + " DESC");

        if (cursor.moveToFirst()) {
            do {
                Patient patient = createPatientFromCursor(cursor);
                patients.add(patient);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return patients;
    }

    /**
     * 检查患者ID是否存在
     */
    public boolean isPatientIdExists(String patientId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.TABLE_PATIENTS, new String[]{DatabaseHelper.COLUMN_ID},
                DatabaseHelper.COLUMN_PATIENT_ID + "=?", new String[]{patientId},
                null, null, null);

        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();
        return exists;
    }

    /**
     * 从Cursor创建Patient对象
     */
    private Patient createPatientFromCursor(Cursor cursor) {
        Patient patient = new Patient();
        patient.setId(cursor.getInt(cursor.getColumnIndex(DatabaseHelper.COLUMN_ID)));
        patient.setPatientId(cursor.getString(cursor.getColumnIndex(DatabaseHelper.COLUMN_PATIENT_ID)));
        patient.setPatientName(cursor.getString(cursor.getColumnIndex(DatabaseHelper.COLUMN_PATIENT_NAME)));

        // 处理新字段，考虑兼容性
        int ageIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_AGE);
        if (ageIndex >= 0) {
            patient.setAge(cursor.getInt(ageIndex));
        }

        int genderIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_GENDER);
        if (genderIndex >= 0) {
            patient.setGender(cursor.getString(genderIndex));
        }

        int treatmentIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_TREATMENT_CONTENT);
        if (treatmentIndex >= 0) {
            patient.setTreatmentContent(cursor.getString(treatmentIndex));
        }

        int remarkIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_REMARK);
        if (remarkIndex >= 0) {
            patient.setRemark(cursor.getString(remarkIndex));
        }

        patient.setFrequency(cursor.getInt(cursor.getColumnIndex(DatabaseHelper.COLUMN_FREQUENCY)));
        patient.setChannelIntensities(cursor.getString(cursor.getColumnIndex(DatabaseHelper.COLUMN_CHANNEL_INTENSITIES)));
        patient.setCreateTime(cursor.getLong(cursor.getColumnIndex(DatabaseHelper.COLUMN_CREATE_TIME)));
        patient.setUpdateTime(cursor.getLong(cursor.getColumnIndex(DatabaseHelper.COLUMN_UPDATE_TIME)));
        return patient;
    }
}