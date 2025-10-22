package com.example.acupunctureinstrument;

/**
 * 患者实体类
 * 文件名：Patient.java
 */
public class Patient {
    private int id;
    private String patientId;        // 患者ID
    private String patientName;      // 患者姓名
    private int age;                 // 年龄
    private String gender;           // 性别
    private String treatmentContent; // 治疗内容
    private String remark;           // 备注
    private int frequency;           // 频率
    private String channelIntensities; // 12路通道强度（JSON格式存储）
    private long createTime;         // 创建时间
    private long updateTime;         // 更新时间

    public Patient() {
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
    }

    public Patient(String patientId, String patientName, int age, String gender,
                   String treatmentContent, String remark, int frequency, String channelIntensities) {
        this.patientId = patientId;
        this.patientName = patientName;
        this.age = age;
        this.gender = gender;
        this.treatmentContent = treatmentContent;
        this.remark = remark;
        this.frequency = frequency;
        this.channelIntensities = channelIntensities;
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getTreatmentContent() {
        return treatmentContent;
    }

    public void setTreatmentContent(String treatmentContent) {
        this.treatmentContent = treatmentContent;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public String getChannelIntensities() {
        return channelIntensities;
    }

    public void setChannelIntensities(String channelIntensities) {
        this.channelIntensities = channelIntensities;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "Patient{" +
                "id=" + id +
                ", patientId='" + patientId + '\'' +
                ", patientName='" + patientName + '\'' +
                ", age=" + age +
                ", gender='" + gender + '\'' +
                ", treatmentContent='" + treatmentContent + '\'' +
                ", remark='" + remark + '\'' +
                ", frequency=" + frequency +
                ", channelIntensities='" + channelIntensities + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}