package com.gyb.xqfx.service.acad;

import lombok.Data;

/**
 * 教务同步结果
 */
@Data
public class AcadSyncResult {

    private String provider;
    private String itemType;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String message;
    private String detail;

    public static AcadSyncResult ok(String provider, String itemType, int total, int success, String detail) {
        AcadSyncResult r = new AcadSyncResult();
        r.setProvider(provider);
        r.setItemType(itemType);
        r.setTotalCount(total);
        r.setSuccessCount(success);
        r.setFailCount(total - success);
        r.setMessage("同步成功");
        r.setDetail(detail);
        return r;
    }

    public static AcadSyncResult fail(String provider, String itemType, String message) {
        AcadSyncResult r = new AcadSyncResult();
        r.setProvider(provider);
        r.setItemType(itemType);
        r.setTotalCount(0);
        r.setSuccessCount(0);
        r.setFailCount(0);
        r.setMessage(message);
        return r;
    }
}
