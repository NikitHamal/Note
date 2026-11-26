package com.neb.ians.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neb.ians.R

/**
 * The website's inline icon set (2px round stroke on a 24 grid), as vector drawables
 * generated from the web templates by `tools/design/svg_to_vector.py`. Use these
 * wherever the web shows the same glyph so both clients read as one product.
 */
object NebIcons {
    @DrawableRes val Image = R.drawable.ic_neb_image
    @DrawableRes val Video = R.drawable.ic_neb_video
    @DrawableRes val Poll = R.drawable.ic_neb_poll
    @DrawableRes val Book = R.drawable.ic_neb_book
    @DrawableRes val Close = R.drawable.ic_neb_close
    @DrawableRes val Send = R.drawable.ic_neb_send
    @DrawableRes val EyeOff = R.drawable.ic_neb_eye_off
    @DrawableRes val Eye = R.drawable.ic_neb_eye
    @DrawableRes val Pen = R.drawable.ic_neb_pen
    @DrawableRes val Check = R.drawable.ic_neb_check
    @DrawableRes val ChevronDown = R.drawable.ic_neb_chevron_down
    @DrawableRes val Bold = R.drawable.ic_neb_bold
    @DrawableRes val Italic = R.drawable.ic_neb_italic
    @DrawableRes val Strike = R.drawable.ic_neb_strike
    @DrawableRes val Underline = R.drawable.ic_neb_underline
    @DrawableRes val Heading = R.drawable.ic_neb_heading
    @DrawableRes val Code = R.drawable.ic_neb_code
    @DrawableRes val CodeBlock = R.drawable.ic_neb_code_block
    @DrawableRes val Math = R.drawable.ic_neb_math
    @DrawableRes val Quote = R.drawable.ic_neb_quote
    @DrawableRes val ListBullet = R.drawable.ic_neb_list_bullet
    @DrawableRes val ListNumber = R.drawable.ic_neb_list_number
    @DrawableRes val Link = R.drawable.ic_neb_link
    @DrawableRes val Divider = R.drawable.ic_neb_divider
    @DrawableRes val Comment = R.drawable.ic_neb_comment
    @DrawableRes val Share = R.drawable.ic_neb_share
}

/** One web-set glyph, tinted by the surrounding content colour unless told otherwise. */
@Composable
fun NebIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size)
    )
}
