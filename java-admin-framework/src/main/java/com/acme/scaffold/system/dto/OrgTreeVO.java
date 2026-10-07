package com.acme.scaffold.system.dto;

import java.util.List;

public record OrgTreeVO(Long id, Long parentId, String orgCode, String orgName, String orgType,
                        Integer sortNo, String status, Long leaderUserId, List<OrgTreeVO> children) {
}
