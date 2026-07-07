package top.szzz666.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一 API 响应封装
 */
@Data
public class ApiResponse {
    private int code;
    private String message;
    private Object data;
    private long timestamp = System.currentTimeMillis();

    public static ApiResponse success() {
        return success(null);
    }

    public static ApiResponse success(Object data) {
        ApiResponse resp = new ApiResponse();
        resp.code = 0;
        resp.message = "success";
        resp.data = data;
        return resp;
    }

    public static ApiResponse success(Object data, String message) {
        ApiResponse resp = new ApiResponse();
        resp.code = 0;
        resp.message = message;
        resp.data = data;
        return resp;
    }

    public static ApiResponse error(String message) {
        return error(500, message);
    }

    public static ApiResponse error(int code, String message) {
        ApiResponse resp = new ApiResponse();
        resp.code = code;
        resp.message = message;
        return resp;
    }

    /**
     * 分页数据响应
     */
    public static ApiResponse page(Object rows, long total, int page, int pageSize) {
        Map<String, Object> pageData = new HashMap<>();
        pageData.put("rows", rows);
        pageData.put("total", total);
        pageData.put("page", page);
        pageData.put("pageSize", pageSize);
        pageData.put("totalPages", (long) Math.ceil((double) total / pageSize));
        return success(pageData);
    }
}
