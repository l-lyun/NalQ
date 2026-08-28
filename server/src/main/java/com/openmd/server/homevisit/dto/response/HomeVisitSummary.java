package com.openmd.server.homevisit.dto.response;

import java.time.LocalDate;

public record HomeVisitSummary(LocalDate visitDate, int consecutiveVisitDays) {}
