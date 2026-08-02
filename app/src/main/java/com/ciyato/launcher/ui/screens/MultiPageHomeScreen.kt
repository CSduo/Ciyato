package com.ciyato.launcher.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ciyato.launcher.ui.theme.CiyatoGold
import com.ciyato.launcher.ui.theme.CiyatoMuted

/**
 * MultiPageHomeScreen — Suggestion #22
 * Scrollable home screen pages using HorizontalPager (Compose Foundation).
 * Each page shows a different app group (All, Work, Social, Entertainment, etc.).
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "Page ${pagerState.currentPage + 1} of ${pagerState.pageCount}"
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pagerState.pageCount) { i ->
            val isActive = pagerState.currentPage == i
            Box(
                modifier = Modifier
                    .size(if (isActive) 8.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (isActive) CiyatoGold else CiyatoMuted.copy(alpha = 0.4f))
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> MultiPageContainer(
    pages: List<T>,
    modifier: Modifier = Modifier,
    showIndicator: Boolean = true,
    content: @Composable (page: T, pageIndex: Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })

    val directionalConnection = com.ciyato.launcher.ui.components.rememberDirectionalNestedScrollConnection()

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(directionalConnection),
        ) { pageIndex ->
            content(pages[pageIndex], pageIndex)
        }

        if (showIndicator && pages.size > 1) {
            PageIndicator(
                pagerState = pagerState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }
    }
}
