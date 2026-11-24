package com.kitsuneapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitsuneapp.R
import com.kitsuneapp.data.CategoryIcon

private fun CategoryIcon?.design() = when (this) {
    null -> "默认" to R.drawable.ks_tags
    CategoryIcon.FOOD -> "餐饮" to R.drawable.ks_utensils
    CategoryIcon.TRANSPORT -> "交通" to R.drawable.ks_train_front
    CategoryIcon.HOME -> "居家" to R.drawable.ks_house
    CategoryIcon.WORK -> "工作" to R.drawable.ks_briefcase_business
    CategoryIcon.MONEY -> "财务" to R.drawable.ks_wallet
    CategoryIcon.SHOPPING -> "购物" to R.drawable.ks_shopping_bag
    CategoryIcon.HEALTH -> "健康" to R.drawable.ks_heart_pulse
    CategoryIcon.GIFT -> "礼物" to R.drawable.ks_gift
    CategoryIcon.EDUCATION -> "教育" to R.drawable.ks_graduation_cap
    CategoryIcon.TRAVEL -> "旅行" to R.drawable.ks_plane
    CategoryIcon.ENTERTAINMENT -> "娱乐" to R.drawable.ks_gamepad_2
}

@Composable
internal fun categoryGlyph(icon: CategoryIcon?) = designIcon(icon.design().second)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryIconPicker(selected: CategoryIcon?, set: (CategoryIcon?) -> Unit, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel("分类图标")
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (listOf(null) + CategoryIcon.entries).forEach { option ->
                val (label, drawable) = option.design()
                Choice(label, { set(option) }, selected = option == selected, enabled = enabled, icon = designIcon(drawable))
            }
        }
    }
}
