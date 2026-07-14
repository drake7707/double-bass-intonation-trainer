package be.drakarah.intonation.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Single source of truth for spacing across all screens.
 * Use these constants instead of hardcoded dp values to ensure consistency.
 */
object Spacing {
    // Screen edges
    val SCREEN_EDGE_HORIZONTAL = 24.dp
    val SCREEN_EDGE_TOP = 24.dp
    val SCREEN_EDGE_BOTTOM = 16.dp

    // Card & component padding
    val CARD_PADDING = 16.dp
    val CARD_PADDING_SMALL = 12.dp  // Only for very compact items

    // Vertical spacing (Column arrangement)
    val SECTION_BREAK = 20.dp  // Major break between titled groups
    val ITEM_SPACING = 12.dp   // Default vertical space between items
    val FINE_SPACING = 8.dp    // Related elements, fine details

    // Horizontal spacing (Row arrangement)
    val ITEM_HORIZONTAL = 8.dp  // Between chips, buttons in a row
    val COMPONENT_SPACING = 4.dp // Icon + text, very tight

    // Size constants for interactive elements
    val PROGRESS_DOT_SIZE = 24.dp  // Increased for distance readability (Sarah's feedback)
    val PROGRESS_DOT_SPACING = 12.dp
}
