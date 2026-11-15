package yancey.chelper.data.library

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import yancey.chelper.data.remote.library.model.RedeemActivityRequest
import yancey.chelper.data.remote.library.service.CommandLabUserService
import yancey.chelper.domain.library.*
import yancey.chelper.domain.library.model.*

internal class NetworkActivityRepository(
    private val user: CommandLabUserService,
    private val requests: AccountRequests,
) : ActivityRepository {
    override suspend fun dashboard() = requests.run {
        coroutineScope {
            val config = async {
                user.getActivityConfig().outcome { it?.toDomain() ?: error("活动配置为空") }
            }
            val summary = async {
                user.getMyActivity().outcome { it?.toDomain() ?: error("活动信息为空") }
            }
            val ledger = async {
                user.getActivityLedger().outcome { it?.toDomain() ?: error("活动流水为空") }
            }
            val redemptions = async {
                user.getActivityRedemptions().outcome { it?.toDomain() ?: error("兑换记录为空") }
            }
            val tiers = async {
                user.getMyTierDetails().outcome { it?.toDomain() ?: error("段位详情为空") }
            }
            ActivityDashboard(
                config.await(),
                summary.await(),
                ledger.await(),
                redemptions.await(),
                tiers.await(),
            )
        }
    }

    override suspend fun ledger(cursor: Int?) = requests.run {
        user.getActivityLedger(cursor).outcome { it?.toDomain() ?: error("活动流水为空") }
    }

    override suspend fun redeem(productId: String, fulfillmentInfo: String?) = requests.run {
        user.redeemActivityProduct(productId, RedeemActivityRequest(fulfillmentInfo)).outcome {
            it?.toDomain() ?: error("兑换结果为空")
        }
    }
}
