@file:JvmName("StreamInfoDescription")

package org.schabi.newpipe.fragments.detail

import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo

fun descriptionOf(info: StreamInfo?): Description? = info?.description

fun tagsOf(info: StreamInfo?): List<String> = info?.tags.orEmpty()
