package top.szzz666.model;

import lombok.Data;

/**
 * 数据库索引信息
 */
@Data
public class IndexInfo {
    /** 索引名 */
    private String name;
    /** 所属列名 */
    private String columnName;
    /** 是否唯一索引 */
    private boolean unique;
    /** 是否主键 */
    private boolean primaryKey;
    /** 排序方式：A升序/D降序 */
    private String ascOrDesc;
    /** 索引中的序号 */
    private int ordinal;
}
