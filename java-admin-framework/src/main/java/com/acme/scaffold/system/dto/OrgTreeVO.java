package com.acme.scaffold.system.dto;

import java.util.List;

public record OrgTreeVO(Long id, Long parentId, String orgCode, String orgName, String orgType,
                        Integer sortNo, String status, List<OrgTreeVO> children) {
}
