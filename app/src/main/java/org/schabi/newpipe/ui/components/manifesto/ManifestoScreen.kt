package org.schabi.newpipe.ui.components.manifesto

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.schabi.newpipe.R

private val ContentMaxWidth = 640.dp
private val RichTextToken = Regex("""\*\*(.+?)\*\*|\[(\d+)]""")

private fun richText(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in RichTextToken.findAll(text)) {
        val emph = match.groups[1]
        val preceding = text.substring(last, match.range.first)
        append(if (emph == null) preceding.trimEnd() else preceding)
        if (emph != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = accent)) {
                append(emph.value)
            }
        } else {
            withStyle(
                SpanStyle(
                    color = accent,
                    fontSize = 0.7.em,
                    fontWeight = FontWeight.SemiBold,
                    baselineShift = BaselineShift.Superscript
                )
            ) { append(match.groupValues[2]) }
        }
        last = match.range.last + 1
    }
    append(text, last, text.length)
}

@Composable
fun ManifestoScreen() {
    val scroll = rememberScrollState()
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            DisclaimerBanner()

            HeroHeader()

            ManifestoSection(number = 1, title = stringResource(R.string.manifesto_s1_title)) {
                Paragraph(stringResource(R.string.manifesto_s1_p1))
                Paragraph(stringResource(R.string.manifesto_s1_p2))
                Paragraph(stringResource(R.string.manifesto_s1_p3))
            }

            SectionDivider()

            ManifestoSection(number = 2, title = stringResource(R.string.manifesto_s2_title)) {
                Paragraph(stringResource(R.string.manifesto_s2_p1))
                Paragraph(stringResource(R.string.manifesto_s2_p2))
                Paragraph(stringResource(R.string.manifesto_s2_p3))
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

            SectionDivider()

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

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToGoFurtherSection()
                NotesSection()
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DisclaimerBanner() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.manifesto_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeroHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(R.drawable.logo_small),
            contentDescription = null,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.manifesto_hero_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.manifesto_hero_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ManifestoSection(
    number: Int,
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        content()
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = richText(text, MaterialTheme.colorScheme.primary),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun BulletItem(title: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TournesolProjectCard() {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.logo_small),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.manifesto_tournesol_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.manifesto_tournesol_body_prefix))
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("tournesol.app") }
                    append(stringResource(R.string.manifesto_tournesol_body_suffix))
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.manifesto_tournesol_body2),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { uriHandler.openUri("https://tournesol.app") },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Text("tournesol.app")
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
            }
        }
    }
}

@Composable
private fun WantToHelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.manifesto_help_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            HelpItem(
                stringResource(R.string.manifesto_help_1min_title),
                AnnotatedString(stringResource(R.string.manifesto_help_1min_detail))
            )
            HelpItem(
                stringResource(R.string.manifesto_help_2min_title),
                AnnotatedString(stringResource(R.string.manifesto_help_2min_detail))
            )
            HelpItem(
                stringResource(R.string.manifesto_help_5min_title),
                AnnotatedString(stringResource(R.string.manifesto_help_5min_detail))
            )
            HelpItem(
                stringResource(R.string.manifesto_help_regular_title),
                AnnotatedString(stringResource(R.string.manifesto_help_regular_detail))
            )
            val linkStyle = TextLinkStyles(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                )
            )
            HelpItem(
                stringResource(R.string.manifesto_help_more_title),
                buildAnnotatedString {
                    append(stringResource(R.string.manifesto_help_more_prefix))
                    withLink(LinkAnnotation.Url("https://tournesol.app/actions", linkStyle)) {
                        append("tournesol.app/actions")
                    }
                }
            )
        }
    }
}

@Composable
private fun HelpItem(time: String, description: AnnotatedString) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CollapsibleCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    content()
                }
            }
        }
    }
}

@Composable
private fun ToGoFurtherSection() {
    CollapsibleCard(
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        title = stringResource(R.string.manifesto_further_title)
    ) {
        ReferenceItem(
            title = "Prosocial Media",
            author = "A. Tang (2025)",
            url = "https://arxiv.org/abs/2502.10834"
        )
        ReferenceItem(
            title = "Tournesol: A quest for a large, secure and trustworthy database of reliable human judgments",
            author = "(2021)",
            url = "https://arxiv.org/abs/2107.07334"
        )
        ReferenceItem(
            title = "La Dictature des Algorithmes, Une transition démocratique est possible",
            author = "J.-L. Fourquet, L. N. Hoang (2023)",
            url = "https://tallandier.com/livre/la-dictature-des-algorithmes/"
        )
    }
}

@Composable
private fun ReferenceItem(title: String, author: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { uriHandler.openUri(url) }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NotesSection() {
    CollapsibleCard(
        icon = Icons.AutoMirrored.Outlined.StickyNote2,
        title = stringResource(R.string.manifesto_notes_title)
    ) {
        NoteEntry("1", stringResource(R.string.manifesto_note1))
        NoteEntry("2", stringResource(R.string.manifesto_note2))
        NoteEntry("3", stringResource(R.string.manifesto_note3))
        NoteEntry("4", stringResource(R.string.manifesto_note4))
        NoteEntry("5", stringResource(R.string.manifesto_note5))
    }
}

@Composable
private fun NoteEntry(ref: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = ref,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
