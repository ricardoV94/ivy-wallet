package com.ivy.core.ui.account.edit.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.core.ui.R
import com.ivy.design.l0_system.UI
import com.ivy.design.l1_buildingBlocks.SpacerVer
import com.ivy.design.l2_components.modal.IvyModal
import com.ivy.design.l2_components.modal.Modal
import com.ivy.design.l2_components.modal.components.Body
import com.ivy.design.l2_components.modal.components.Title
import com.ivy.design.l2_components.modal.rememberIvyModal
import com.ivy.design.l3_ivyComponents.Feeling
import com.ivy.design.l3_ivyComponents.Visibility
import com.ivy.design.l3_ivyComponents.button.ButtonSize
import com.ivy.design.l3_ivyComponents.button.IvyButton
import com.ivy.design.util.IvyPreview

@Composable
fun BoxScope.DeleteAccountModal(
    modal: IvyModal,
    level: Int = 1,
    accountName: String,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Modal(
        modal = modal,
        level = level,
        actions = {
            IvyButton(
                size = ButtonSize.Small,
                visibility = Visibility.Focused,
                feeling = Feeling.Negative,
                text = "Delete forever",
                icon = R.drawable.ic_round_delete_forever_24
            ) {
                modal.hide()
                onDelete()
            }
        }
    ) {
        Title(
            text = "Delete \"$accountName\" forever?",
            color = UI.colors.red
        )
        SpacerVer(height = 24.dp)
        Body(
            text = "WARNING! Deleting \"$accountName\" account will deleted all transactions" +
                    " in it forever. This operation CANNOT be undone and will affect your balance!" +
                    " Please, be careful otherwise you may lose your data.\n\n" +
                    "If you just don't want to see this account but preserve its transactions," +
                    " a better option would be to just archive it."
        )
        SpacerVer(height = 12.dp)
        IvyButton(
            modifier = Modifier.padding(horizontal = 16.dp),
            size = ButtonSize.Big,
            visibility = Visibility.Medium,
            feeling = Feeling.Positive,
            text = "Archive",
            icon = R.drawable.round_archive_24
        ) {
            modal.hide()
            onArchive()
        }
        // Archive
        SpacerVer(height = 48.dp)
    }
}


// region Preview
@Preview
@Composable
private fun Preview() {
    IvyPreview {
        val modal = rememberIvyModal()
        modal.show()
        DeleteAccountModal(
            modal = modal,
            accountName = "Account 1",
            onArchive = {},
            onDelete = {}
        )
    }
}
// endregion