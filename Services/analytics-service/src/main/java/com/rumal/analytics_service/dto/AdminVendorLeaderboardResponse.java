package com.rumal.analytics_service.dto;

import com.rumal.analytics_service.client.dto.*;
import java.util.List;

public record AdminVendorLeaderboardResponse(
    VendorPlatformSummary summary,
    List<VendorLeaderboardEntry> leaderboard
) {}
