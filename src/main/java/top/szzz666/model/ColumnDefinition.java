package top.szzz666.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 建表/改表的列定义
 */
@Data
public class ColumnDefinition {
    /** 列名 */
    private String name;
    /** 数据类型，如 VARCHAR、INT */
    private String type;
    /** 长度 */
    private int length;
    /** 小数位 */
    private int scale;
    /** 是否允许NULL */
    private boolean nullable = true;
    /** 是否自增 */
    private boolean autoIncrement = false;
    /** 是否主键 */
    private boolean primaryKey = false;
    /** 是否唯一 */
    private boolean unique = false;
    /** 默认值 */
    private String defaultValue;
    /** 注释 */
    private String comment;
    /** 操作类型（用于改表）：ADD/ALTER/DROP/RENAME */
    private String action;
    /** 旧列名（用于改名） */
    private String oldName;

    /**
     * 表结构变更定义
     */
    @Data
    public static class TableAlterDefinition {
        /** 操作列表 */
        private List<ColumnDefinition> columns = new ArrayList<>();
        /** 是否删除并重建（SQLite改表特殊情况） */
        private boolean rebuild = false;
        /** 新表名（重命名表） */
        private String newTableName;
    }
}
