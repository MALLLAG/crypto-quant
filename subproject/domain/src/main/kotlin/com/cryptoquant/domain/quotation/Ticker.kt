package com.cryptoquant.domain.quotation

import com.cryptoquant.domain.common.Amount
import com.cryptoquant.domain.common.ChangeRate
import com.cryptoquant.domain.common.Price
import com.cryptoquant.domain.common.PriceChange
import com.cryptoquant.domain.common.TradingPair
import com.cryptoquant.domain.common.Volume
import java.time.Instant

/**
 * 현재가 정보.
 *
 * 업비트 시세 API의 현재가(ticker) 응답을 나타냅니다.
 *
 * 주의: changePrice/changeRate는 업비트 API에서 절대값(항상 양수)으로 제공됩니다.
 * PriceChange/ChangeRate 타입 자체는 음수를 허용하지만, 이 필드들은 항상 양수입니다.
 * 하락 여부는 change 필드(RISE/EVEN/FALL)로 확인하세요.
 *
 * @property change 전일 대비 변동 방향 (RISE: 상승, EVEN: 보합, FALL: 하락)
 * @property changePrice 전일 대비 변화량 (절대값, 업비트 API에서 항상 양수로 제공)
 * @property changeRate 전일 대비 변화율 (절대값, 업비트 API에서 항상 양수로 제공)
 * @property signedChangePrice 전일 대비 변화량 (부호 포함, 하락 시 음수)
 * @property signedChangeRate 전일 대비 변화율 (부호 포함, 하락 시 음수)
 */
data class Ticker(
    val pair: TradingPair,
    val tradePrice: Price,
    val openingPrice: Price,
    val highPrice: Price,
    val lowPrice: Price,
    val prevClosingPrice: Price,
    val change: Change,
    val changePrice: PriceChange,
    val changeRate: ChangeRate,
    val signedChangePrice: PriceChange,
    val signedChangeRate: ChangeRate,
    val tradeVolume: Volume,
    val accTradePrice24h: Amount,
    val accTradeVolume24h: Volume,
    val timestamp: Instant,
)
