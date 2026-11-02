package org.schabi.newpipe.ui.components.manifesto

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.R

private val SunflowerYellow = Color(0xFFF1C40F)
private val SunflowerDark = Color(0xFF8A6D0B)

private fun parseEmph(text: String): AnnotatedString = buildAnnotatedString {
    val emphStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
        textDecoration = TextDecoration.Underline,
        color = SunflowerDark
    )
    var remaining = text
    while (remaining.contains("**")) {
        val start = remaining.indexOf("**")
        append(remaining.substring(0, start))
        remaining = remaining.substring(start + 2)
        val end = remaining.indexOf("**")
        if (end < 0) {
            append("**$remaining")
            return@buildAnnotatedString
        }
        withStyle(emphStyle) { append(remaining.substring(0, end)) }
        remaining = remaining.substring(end + 2)
    }
    append(remaining)
}

@Composable
fun ManifestoScreen() {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeroCard()

        ManifestoSection(number = 1, title = stringResource(R.string.manifesto_s1_title)) {
            Paragraph(parseEmph(stringResource(R.string.manifesto_s1_p1)))
            Paragraph(stringResource(R.string.manifesto_s1_p2))
            Paragraph(stringResource(R.string.manifesto_s1_p3))
        }

        ManifestoSection(number = 2, title = stringResource(R.string.manifesto_s2_title)) {
            Paragraph(stringResource(R.string.manifesto_s2_p1))
            Paragraph(parseEmph(stringResource(R.string.manifesto_s2_p2)))
            Paragraph(parseEmph(stringResource(R.string.manifesto_s2_p3)))
            BulletItem(
                stringResource(R.string.manifesto_s2_b1_title),
                stringResource(R.string.manifesto_s2_b1_detail)
            )
            BulletItem(
                stringResource(R.string.manifesto_s2_b2_title),
                stringResource(R.string.manifesto_s2_b2_detail)
            )
            BulletItem(
                stringResource(R.string.manifesto_s2_b3_title),
                stringResource(R.string.manifesto_s2_b3_detail)
            )
        }

        ManifestoSection(number = 3, title = stringResource(R.string.manifesto_s3_title)) {
            Paragraph(stringResource(R.string.manifesto_s3_p1))
            BulletItem(
                stringResource(R.string.manifesto_s3_b1_title),
                stringResource(R.string.manifesto_s3_b1_detail)
            )
            BulletItem(
                stringResource(R.string.manifesto_s3_b2_title),
                stringResource(R.string.manifesto_s3_b2_detail)
            )
            BulletItem(
                stringResource(R.string.manifesto_s3_b3_title),
                stringResource(R.string.manifesto_s3_b3_detail)
            )
            BulletItem(
                stringResource(R.string.manifesto_s3_b4_title),
                stringResource(R.string.manifesto_s3_b4_detail)
            )
            Paragraph(stringResource(R.string.manifesto_s3_p2))
            Paragraph(stringResource(R.string.manifesto_s3_p3))
        }

        TournesolProjectCard()

        WantToHelpCard()

        ToGoFurtherSection()

        NotesSection()

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HeroCard() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = SunflowerYellow,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Campaign,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.manifesto_hero_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.manifesto_tab_description),
            style = MaterialTheme.typography.labelLarge,
            color = SunflowerDark,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.manifesto_hero_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ManifestoSection(
    number: Int,
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(SunflowerYellow, CircleShape)
                ) {
                    Text(
                        text = number.toString(),
                        color = SunflowerDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Justify
    )
}

@Composable
private fun Paragraph(text: AnnotatedString) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Justify
    )
}

@Composable
private fun BulletItem(title: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp, end = 10.dp)
                .size(6.dp)
                .background(SunflowerYellow, CircleShape)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TournesolProjectCard() {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri("https://tournesol.app") },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SunflowerDark)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ArrowCircleRight,
                    contentDescription = null,
                    tint = SunflowerYellow,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.manifesto_tournesol_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.manifesto_tournesol_body_prefix))
                    withStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold
                        )
                    ) { append("tournesol.app") }
                    append(stringResource(R.string.manifesto_tournesol_body_suffix))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = stringResource(R.string.manifesto_tournesol_body2),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f)
            )
        }
    }
}

@Composable
private fun WantToHelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.manifesto_help_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            HelpItem(
                stringResource(R.string.manifesto_help_1min_title),
                stringResource(R.string.manifesto_help_1min_detail)
            )
            HelpItem(
                stringResource(R.string.manifesto_help_2min_title),
                stringResource(R.string.manifesto_help_2min_detail)
            )
            HelpItem(
                stringResource(R.string.manifesto_help_5min_title),
                stringResource(R.string.manifesto_help_5min_detail)
            )
            HelpItem(
                stringResource(R.string.manifesto_help_regular_title),
                stringResource(R.string.manifesto_help_regular_detail)
            )
            HelpItemWithLink(
                time = stringResource(R.string.manifesto_help_more_title),
                prefix = stringResource(R.string.manifesto_help_more_prefix),
                linkText = "tournesol.app/actions",
                url = "https://tournesol.app/actions"
            )
        }
    }
}

@Composable
private fun HelpItem(time: String, description: String) {
    Column {
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = SunflowerDark
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun HelpItemWithLink(time: String, prefix: String, linkText: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Column {
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = SunflowerDark
        )
        Text(
            text = buildAnnotatedString {
                append(prefix)
                withStyle(
                    SpanStyle(
                        color = SunflowerDark,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    )
                ) { append(linkText) }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.clickable { uriHandler.openUri(url) }
        )
    }
}

@Composable
private fun ToGoFurtherSection() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "togofurther")
    val uriHandler = LocalUriHandler.current
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = SunflowerDark)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.manifesto_further_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ReferenceItem(
                        title = "Prosocial Media",
                        author = "(2025). A. Tang.",
                        url = "https://arxiv.org/abs/2502.10834",
                        uriHandler = uriHandler
                    )
                    ReferenceItem(
                        title = "Tournesol: A quest for a large, secure and trustworthy database of reliable human judgments",
                        author = "(2021).",
                        url = "https://arxiv.org/abs/2107.07334",
                        uriHandler = uriHandler
                    )
                    ReferenceItem(
                        title = "La Dictature des Algorithmes, Une transition démocratique est possible",
                        author = "(2023). J.-L. Fourquet, L. N. Hoang.",
                        url = "https://tallandier.com/livre/la-dictature-des-algorithmes/",
                        uriHandler = uriHandler
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceItem(
    title: String,
    author: String,
    url: String,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(url) }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.manifesto_further_link),
            style = MaterialTheme.typography.labelMedium,
            color = SunflowerDark,
            textDecoration = TextDecoration.Underline
        )
    }
}

@Composable
private fun NotesSection() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "notes")
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.StickyNote2, contentDescription = null, tint = SunflowerDark)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.manifesto_notes_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NoteEntry("[1]", stringResource(R.string.manifesto_note1))
                    NoteEntry("[2]", stringResource(R.string.manifesto_note2))
                    NoteEntry("[3]", stringResource(R.string.manifesto_note3))
                    NoteEntry("[4]", stringResource(R.string.manifesto_note4))
                    NoteEntry("[5]", stringResource(R.string.manifesto_note5))
                }
            }
        }
    }
}

@Composable
private fun NoteEntry(ref: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = ref,
            style = MaterialTheme.typography.labelLarge,
            color = SunflowerDark,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Suppress("unused")
private val UnusedPadding = PaddingValues(0.dp)
