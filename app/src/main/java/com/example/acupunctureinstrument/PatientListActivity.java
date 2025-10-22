package com.example.acupunctureinstrument;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import android.util.Log;
/**
 * 患者列表页面
 * 文件名：PatientListActivity.java
 */
public class PatientListActivity extends AppCompatActivity implements PatientAdapter.OnPatientClickListener {

    private RecyclerView recyclerView;
    private PatientAdapter adapter;
    private PatientDao patientDao;
    private List<Patient> allPatientList;  // 所有患者列表
    private List<Patient> filteredPatientList;  // 过滤后的患者列表

    // 搜索相关控件
    private EditText etSearch;
    private Button btnClearSearch;
    private TextView tvSearchResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_list);

        initViews();
        loadPatients();
        setupSearch();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view_patients);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 搜索相关控件
        etSearch = findViewById(R.id.et_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        tvSearchResult = findViewById(R.id.tv_search_result);

        patientDao = new PatientDao(this);

        // 添加返回按钮点击事件
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 清除搜索按钮点击事件
        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            btnClearSearch.setVisibility(View.GONE);
            tvSearchResult.setVisibility(View.GONE);
            showAllPatients();
        });
    }

    private void loadPatients() {
        allPatientList = patientDao.getAllPatients();
        filteredPatientList = new ArrayList<>(allPatientList);

        if (allPatientList.isEmpty()) {
            Toast.makeText(this, "暂无患者记录", Toast.LENGTH_SHORT).show();
        }

        adapter = new PatientAdapter(filteredPatientList, this);
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String searchText = s.toString().trim();
                if (searchText.isEmpty()) {
                    btnClearSearch.setVisibility(View.GONE);
                    tvSearchResult.setVisibility(View.GONE);
                    showAllPatients();
                } else {
                    btnClearSearch.setVisibility(View.VISIBLE);
                    filterPatients(searchText);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void filterPatients(String searchText) {
        filteredPatientList.clear();
        String searchLower = searchText.toLowerCase();

        for (Patient patient : allPatientList) {
            // 搜索患者ID
            if (patient.getPatientId() != null &&
                    patient.getPatientId().toLowerCase().contains(searchLower)) {
                filteredPatientList.add(patient);
                continue;
            }

            // 搜索患者姓名
            if (patient.getPatientName() != null &&
                    patient.getPatientName().toLowerCase().contains(searchLower)) {
                filteredPatientList.add(patient);
                continue;
            }

            // 搜索治疗内容
            if (patient.getTreatmentContent() != null &&
                    patient.getTreatmentContent().toLowerCase().contains(searchLower)) {
                filteredPatientList.add(patient);
                continue;
            }

            // 搜索性别
            if (patient.getGender() != null &&
                    patient.getGender().toLowerCase().contains(searchLower)) {
                filteredPatientList.add(patient);
                continue;
            }

            // 搜索备注
            if (patient.getRemark() != null &&
                    patient.getRemark().toLowerCase().contains(searchLower)) {
                filteredPatientList.add(patient);
                continue;
            }

            // 搜索年龄（转换为字符串）
            if (String.valueOf(patient.getAge()).contains(searchText)) {
                filteredPatientList.add(patient);
                continue;
            }

            // 搜索频率（转换为字符串）
            if (String.valueOf(patient.getFrequency()).contains(searchText)) {
                filteredPatientList.add(patient);
                continue;
            }
        }

        // 更新搜索结果提示
        tvSearchResult.setVisibility(View.VISIBLE);
        tvSearchResult.setText("共找到 " + filteredPatientList.size() + " 条记录");

        // 刷新列表
        adapter.notifyDataSetChanged();

        // 如果没有搜索结果，显示提示
        if (filteredPatientList.isEmpty()) {
            Toast.makeText(this, "未找到匹配的患者记录", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAllPatients() {
        filteredPatientList.clear();
        filteredPatientList.addAll(allPatientList);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onPatientClick(Patient patient) {
        // 点击患者记录时的处理
        showPatientDetailsDialog(patient);
    }

    @Override
    public void onLoadParametersClick(Patient patient) {
        Log.d("DEBUG_LOAD", "=== 加载数据调试 ===");
        Log.d("DEBUG_LOAD", "患者: " + patient.getPatientName());
        Log.d("DEBUG_LOAD", "数据库中的频率: " + patient.getFrequency() + "Hz");
        Log.d("DEBUG_LOAD", "数据库中的通道强度JSON: " + patient.getChannelIntensities());

        try {
            // 解析通道强度JSON
            JSONArray jsonArray = new JSONArray(patient.getChannelIntensities());
            int[] channelIntensities = new int[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                channelIntensities[i] = jsonArray.getInt(i);
                Log.d("DEBUG_LOAD", "解析通道" + (i+1) + ": " + channelIntensities[i]);
            }

            // 返回数据到主页面
            Intent resultIntent = new Intent();
            resultIntent.putExtra("frequency", patient.getFrequency());
            resultIntent.putExtra("channel_intensities", channelIntensities);
            resultIntent.putExtra("patient_name", patient.getPatientName());
            resultIntent.putExtra("patient_id", patient.getPatientId());

            Log.d("DEBUG_LOAD", "准备返回数据到MainActivity");

            setResult(RESULT_OK, resultIntent);
            Toast.makeText(this, "已加载 " + patient.getPatientName() + " 的参数", Toast.LENGTH_SHORT).show();
            finish();

        } catch (Exception e) {
            Log.e("DEBUG_LOAD", "参数加载失败", e);
            Toast.makeText(this, "参数加载失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeleteClick(Patient patient) {
        // 删除患者记录
        showDeleteConfirmDialog(patient);
    }

    private void showPatientDetailsDialog(Patient patient) {
        try {
            // 解析通道强度JSON
            JSONArray jsonArray = new JSONArray(patient.getChannelIntensities());
            StringBuilder channelDetails = new StringBuilder();
            for (int i = 0; i < jsonArray.length(); i++) {
                if (i > 0) channelDetails.append("\n");
                channelDetails.append((i + 1)).append("路: ").append(jsonArray.getInt(i));
            }

            // 构建详细信息
            StringBuilder message = new StringBuilder();
            message.append("患者ID: ").append(patient.getPatientId()).append("\n");
            message.append("患者姓名: ").append(patient.getPatientName()).append("\n");

            if (patient.getAge() > 0) {
                message.append("年龄: ").append(patient.getAge()).append("岁\n");
            }

            if (patient.getGender() != null && !patient.getGender().isEmpty()) {
                message.append("性别: ").append(patient.getGender()).append("\n");
            }

            if (patient.getTreatmentContent() != null && !patient.getTreatmentContent().isEmpty()) {
                message.append("治疗内容: ").append(patient.getTreatmentContent()).append("\n");
            }

            if (patient.getRemark() != null && !patient.getRemark().isEmpty()) {
                message.append("备注: ").append(patient.getRemark()).append("\n");
            }

            message.append("频率: ").append(patient.getFrequency()).append("Hz\n");
            message.append("通道强度:\n").append(channelDetails.toString());

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("患者详细信息")
                    .setMessage(message.toString())
                    .setPositiveButton("加载参数", (dialog, which) -> onLoadParametersClick(patient))
                    .setNegativeButton("关闭", null)
                    .show();

        } catch (Exception e) {
            Toast.makeText(this, "显示详情失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteConfirmDialog(Patient patient) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除患者 " + patient.getPatientName() + " 的记录吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    int result = patientDao.deletePatient(patient.getId());
                    if (result > 0) {
                        Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
                        // 从两个列表中都移除
                        allPatientList.remove(patient);
                        filteredPatientList.remove(patient);
                        adapter.notifyDataSetChanged();

                        // 更新搜索结果提示
                        if (!etSearch.getText().toString().isEmpty()) {
                            tvSearchResult.setText("共找到 " + filteredPatientList.size() + " 条记录");
                        }
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 页面恢复时刷新数据
        loadPatients();
        // 如果有搜索内容，重新执行搜索
        String searchText = etSearch.getText().toString().trim();
        if (!searchText.isEmpty()) {
            filterPatients(searchText);
        }
    }
}