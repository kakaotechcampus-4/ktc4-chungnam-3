package com.ktc.chungnam3.remembrall.save.dto;

public record ResolvedPlaceDto(
        String candidateId,

        String name,

        String branchName,

        String address,

        double lat,

        double lng

) {
}
