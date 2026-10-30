package com.acme.scaffold.system.dto;

public record ApiResourceView(Long id, String resourceName, String permissionCode, String httpMethod,
                              String pathPattern, String authMode, String status, String riskLevel) {

    public static ApiResourceView from(com.acme.scaffold.system.entity.SysApiResourceDO a) {
        return new ApiResourceView(a.getId(), a.getResourceName(), a.getPermissionCode(), a.getHttpMethod(),
                a.getPathPattern(), a.getAuthMode(), a.getStatus(), a.getRiskLevel());
    }
}
