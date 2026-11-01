package org.schabi.newpipe.tournesol.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.R
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.ui.components.items.Stream
import org.schabi.newpipe.ui.components.items.stream.StreamThumbnail

@Composable
internal fun CompareHistoryOverlayGridCard(
    entry: StreamHistoryEntry,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = LocalCompareScale.current
    val stream = remember(entry) { Stream(entry.toStreamInfoItem()) }
    val titleColor = compareMetaTitleColor()
    val uploaderColor = compareMetaUploaderColor()
    val cardBackground = colorResource(R.color.tournesol_overlay_card)
    val borderColor = if (selected) {
        accentColor
    } else {
        colorResource(R.color.tournesol_overlay_outline)
    }
    Surface(
        modifier = Modifier
            .then(modifier)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = cardBackground,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp * scale, vertical = 3.dp * scale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreamThumbnail(
                stream = stream,
                showProgress = false,
                showDuration = true,
                durationTextStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 84.dp * scale, height = 48.dp * scale)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp * scale),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.streamEntity.title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 12.sp
                    ),
                    color = titleColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.streamEntity.uploader.isNotBlank()) {
                    Text(
                        text = entry.streamEntity.uploader,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = uploaderColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
