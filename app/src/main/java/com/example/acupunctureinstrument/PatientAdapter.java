package com.example.acupunctureinstrument;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;

/**
 * 患者列表适配器
 * 文件名：PatientAdapter.java
 */
public class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.PatientViewHolder> {

    private List<Patient> patientList;
    private OnPatientClickListener listener;
    private SimpleDateFormat dateFormat;

    public interface OnPatientClickListener {
        void onPatientClick(Patient patient);
        void onLoadParametersClick(Patient patient);
        void onDeleteClick(Patient patient);
    }

    public PatientAdapter(List<Patient> patientList, OnPatientClickListener listener) {
        this.patientList = patientList;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_patient, parent, false);
        return new PatientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
        Patient patient = patientList.get(position);
        holder.bind(patient);
    }

    @Override
    public int getItemCount() {
        return patientList.size();
    }

    class PatientViewHolder extends RecyclerView.ViewHolder {
        private TextView tvPatientId;
        private TextView tvPatientName;
        private TextView tvPatientInfo;
        private TextView tvFrequency;
        private TextView tvChannelSummary;
        private TextView tvUpdateTime;
        private Button btnLoadParameters;
        private Button btnDelete;

        public PatientViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPatientId = itemView.findViewById(R.id.tv_patient_id);
            tvPatientName = itemView.findViewById(R.id.tv_patient_name);
            tvPatientInfo = itemView.findViewById(R.id.tv_patient_info);
            tvFrequency = itemView.findViewById(R.id.tv_frequency);
            tvChannelSummary = itemView.findViewById(R.id.tv_channel_summary);
            tvUpdateTime = itemView.findViewById(R.id.tv_update_time);
            btnLoadParameters = itemView.findViewById(R.id.btn_load_parameters);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }

        public void bind(Patient patient) {
            tvPatientId.setText("ID: " + patient.getPatientId());
            tvPatientName.setText(patient.getPatientName());

            // 显示年龄和性别信息
            StringBuilder patientInfo = new StringBuilder();
            if (patient.getAge() > 0) {
                patientInfo.append(patient.getAge()).append("岁");
            }
            if (patient.getGender() != null && !patient.getGender().isEmpty()) {
                if (patientInfo.length() > 0) patientInfo.append(" | ");
                patientInfo.append(patient.getGender());
            }
            if (patient.getTreatmentContent() != null && !patient.getTreatmentContent().isEmpty()) {
                if (patientInfo.length() > 0) patientInfo.append(" | ");
                // 只显示治疗内容的前20个字符
                String treatment = patient.getTreatmentContent();
                if (treatment.length() > 20) {
                    treatment = treatment.substring(0, 20) + "...";
                }
                patientInfo.append(treatment);
            }

            if (patientInfo.length() > 0) {
                tvPatientInfo.setText(patientInfo.toString());
                tvPatientInfo.setVisibility(View.VISIBLE);
            } else {
                tvPatientInfo.setVisibility(View.GONE);
            }

            tvFrequency.setText("频率: " + patient.getFrequency() + "Hz");
            tvUpdateTime.setText("更新时间: " + dateFormat.format(new Date(patient.getUpdateTime())));

            // 显示通道强度摘要
            try {
                JSONArray jsonArray = new JSONArray(patient.getChannelIntensities());
                StringBuilder summary = new StringBuilder("通道强度: ");

                // 只显示前4个通道的强度，其余用...表示
                int displayCount = Math.min(4, jsonArray.length());
                for (int i = 0; i < displayCount; i++) {
                    if (i > 0) summary.append(", ");
                    summary.append((i + 1)).append("路:").append(jsonArray.getInt(i));
                }

                if (jsonArray.length() > 4) {
                    summary.append("...");
                }

                tvChannelSummary.setText(summary.toString());

            } catch (Exception e) {
                tvChannelSummary.setText("通道强度: 解析失败");
            }

            // 设置点击事件
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPatientClick(patient);
                }
            });

            btnLoadParameters.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLoadParametersClick(patient);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(patient);
                }
            });
        }
    }
}