package dev.hrutvik.filesync.device;
import jakarta.validation.constraints.PositiveOrZero;
public record AcknowledgeCursorRequest(@PositiveOrZero long cursor) {}

