package top.szzz666.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据表信息
 */
@Data
public class TableInfo {
    /** 表名 */
    private String name;
    /** 表注释 */
    private String comment;
    /** 表类型：BASE TABLE / VIEW */
    private String tableType;
    /** 行数（估算） */
    private long rowCount;
    /** 数据大小（字节，MySQL可用） */
    private long dataSize;
    /** 列信息 */
    private List<ColumnInfo> columns = new ArrayList<>();
    /** 主键列名列表 */
    private List<String> primaryKeys = new ArrayList<>();
    /** 创建时间 */
    private String createTime;
    /** 更新时间 */
    private String updateTime;

    public ColumnInfo getColumn(String columnName) {
        if (columns == null) return null;
        return columns.stream()
                .filter(c -> c.getName().equalsIgnoreCase(columnName))
                .findFirst()
                .orElse(null);
    }
}
