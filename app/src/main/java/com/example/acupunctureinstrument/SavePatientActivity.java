package com.example.acupunctureinstrument;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 保存患者数据页面
 * 文件名：SavePatientActivity.java
 */
public class SavePatientActivity extends AppCompatActivity {

    private EditText etPatientId;
    private EditText etPatientName;
    private EditText etAge;
    private Spinner spinnerGender;
    private EditText etTreatmentContent;
    private EditText etRemark;
    private TextView tvFrequency;
    private TextView tvChannelSummary;
    private Button btnSave;
    private Button btnCancel;

    private PatientDao patientDao;
    private int currentFrequency;
    private int[] channelIntensities;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save_patient);

        initViews();
        initData();
        setupListeners();
    }

    private void initViews() {
        etPatientId = findViewById(R.id.et_patient_id);
        etPatientName = findViewById(R.id.et_patient_name);
        etAge = findViewById(R.id.et_age);
        spinnerGender = findViewById(R.id.spinner_gender);
        etTreatmentContent = findViewById(R.id.et_treatment_content);
        etRemark = findViewById(R.id.et_remark);
        tvFrequency = findViewById(R.id.tv_frequency);
        tvChannelSummary = findViewById(R.id.tv_channel_summary);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);

        // 设置性别下拉框
        String[] genders = {"请选择", "男", "女"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, genders);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(adapter);

        patientDao = new PatientDao(this);
    }

    private void initData() {
        // 从Intent获取当前参数
        Intent intent = getIntent();
        currentFrequency = intent.getIntExtra("frequency", 150);
        channelIntensities = intent.getIntArrayExtra("channel_intensities");

        if (channelIntensities == null) {
            channelIntensities = new int[12];
            for (int i = 0; i < 12; i++) {
                channelIntensities[i] = 50; // 默认值
            }
        }

        // 显示当前参数
        tvFrequency.setText("当前频率：" + currentFrequency + "Hz");
        tvChannelSummary.setText("通道强度：" + getChannelSummary());
    }

    private String getChannelSummary() {
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < channelIntensities.length; i++) {
            if (i > 0) summary.append(", ");
            summary.append((i + 1)).append("路:").append(channelIntensities[i]);
        }
        return summary.toString();
    }

    private void setupListeners() {
        btnSave.setOnClickListener(v -> savePatient());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void savePatient() {
        String patientId = etPatientId.getText().toString().trim();
        String patientName = etPatientName.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();
        String gender = spinnerGender.getSelectedItem().toString();
        String treatmentContent = etTreatmentContent.getText().toString().trim();
        String remark = etRemark.getText().toString().trim();

        // 验证必填字段
        if (TextUtils.isEmpty(patientId)) {
            Toast.makeText(this, "请输入患者ID", Toast.LENGTH_SHORT).show();
            etPatientId.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(patientName)) {
            Toast.makeText(this, "请输入患者姓名", Toast.LENGTH_SHORT).show();
            etPatientName.requestFocus();
            return;
        }

        // 处理年龄
        int age = 0;
        if (!TextUtils.isEmpty(ageStr)) {
            try {
                age = Integer.parseInt(ageStr);
                if (age < 0 || age > 150) {
                    Toast.makeText(this, "请输入有效的年龄（0-150）", Toast.LENGTH_SHORT).show();
                    etAge.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的年龄", Toast.LENGTH_SHORT).show();
                etAge.requestFocus();
                return;
            }
        }

        // 处理性别
        if ("请选择".equals(gender)) {
            gender = "";
        }

        // 检查患者ID是否已存在
        if (patientDao.isPatientIdExists(patientId)) {
            // 如果存在，询问是否更新
            showUpdateConfirmDialog(patientId, patientName, age, gender, treatmentContent, remark);
            return;
        }

        // 创建新患者记录
        try {
            String channelIntensitiesJson = createChannelIntensitiesJson();
            Patient patient = new Patient(patientId, patientName, age, gender,
                    treatmentContent, remark, currentFrequency, channelIntensitiesJson);

            long result = patientDao.insertPatient(patient);
            if (result != -1) {
                Toast.makeText(this, "患者数据保存成功", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showUpdateConfirmDialog(String patientId, String patientName,
                                         int age, String gender, String treatmentContent, String remark) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("患者ID已存在，是否更新该患者的参数？")
                .setPositiveButton("更新", (dialog, which) ->
                        updateExistingPatient(patientId, patientName, age, gender, treatmentContent, remark))
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateExistingPatient(String patientId, String patientName,
                                       int age, String gender, String treatmentContent, String remark) {
        try {
            Patient existingPatient = patientDao.getPatientByPatientId(patientId);
            if (existingPatient != null) {
                existingPatient.setPatientName(patientName);
                existingPatient.setAge(age);
                existingPatient.setGender(gender);
                existingPatient.setTreatmentContent(treatmentContent);
                existingPatient.setRemark(remark);
                existingPatient.setFrequency(currentFrequency);
                existingPatient.setChannelIntensities(createChannelIntensitiesJson());

                int result = patientDao.updatePatient(existingPatient);
                if (result > 0) {
                    Toast.makeText(this, "患者数据更新成功", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, "更新失败，请重试", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "更新失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String createChannelIntensitiesJson() throws Exception {
        JSONArray jsonArray = new JSONArray();
        for (int intensity : channelIntensities) {
            jsonArray.put(intensity);
        }
        return jsonArray.toString();
    }
}