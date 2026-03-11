package com.aspect.braveyoutube.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aspect.braveyoutube.data.model.FeedCategory
import com.aspect.braveyoutube.ui.theme.*

/**
 * YouTube-style horizontally scrollable filter chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTabs(
    categories: List<FeedCategory>,
    selectedCategory: FeedCategory?,
    onCategorySelected: (FeedCategory?) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        val allSelected = selectedCategory == null
        FilterChip(
            selected = allSelected,
            onClick = { onCategorySelected(null) },
            label = {
                Text(
                    text = "All",
                    fontSize = 14.sp,
                    fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            shape = RoundedCornerShape(8.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = if (isDarkTheme) ChipDark else ChipLight,
                labelColor = if (isDarkTheme) TextWhite else TextBlack,
                selectedContainerColor = if (isDarkTheme) ChipSelectedDark else ChipSelectedLight,
                selectedLabelColor = if (isDarkTheme) Color.Black else Color.White
            ),
            border = null
        )

        // Category chips
        categories.forEach { category ->
            val isSelected = selectedCategory == category
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                label = {
                    Text(
                        text = category.displayName,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = if (isDarkTheme) ChipDark else ChipLight,
                    labelColor = if (isDarkTheme) TextWhite else TextBlack,
                    selectedContainerColor = if (isDarkTheme) ChipSelectedDark else ChipSelectedLight,
                    selectedLabelColor = if (isDarkTheme) Color.Black else Color.White
                ),
                border = null
            )
        }
    }
}
