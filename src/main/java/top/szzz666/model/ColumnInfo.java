package top.szzz666.model;

import lombok.Data;

/**
 * 数据库列信息
 */
@Data
public class ColumnInfo {
    /** 列名 */
    private String name;
    /** 数据类型 */
    private String type;
    /** 类型长度 */
    private int length;
    /** 精度（小数位数） */
    private int scale;
    /** 是否允许NULL */
    private boolean nullable;
    /** 是否自增 */
    private boolean autoIncrement;
    /** 是否主键 */
    private boolean primaryKey;
    /** 是否唯一 */
    private boolean unique;
    /** 默认值 */
    private String defaultValue;
    /** 注释/备注 */
    private String comment;
    /** 在表中的位置 */
    private int ordinal;

    /**
     * 完整类型描述（含长度），例如 VARCHAR(255)、DECIMAL(10,2)
     */
    public String getFullType() {
        StringBuilder sb = new StringBuilder(type == null ? "" : type.toUpperCase());
        if (length > 0) {
            sb.append("(").append(length);
            if (scale > 0) sb.append(",").append(scale);
            sb.append(")");
        }
        return sb.toString();
    }
}
