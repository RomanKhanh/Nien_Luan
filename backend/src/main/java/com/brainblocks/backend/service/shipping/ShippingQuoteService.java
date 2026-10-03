package com.brainblocks.backend.service.shipping;

import com.brainblocks.backend.dto.response.shipping.ShippingQuoteResponse;
import com.brainblocks.backend.dto.response.shipping.ShippingQuoteResponse.ParcelContentResponse;
import com.brainblocks.backend.dto.response.shipping.ShippingQuoteResponse.ParcelResponse;
import com.brainblocks.backend.entity.Cart;
import com.brainblocks.backend.entity.CartItem;
import com.brainblocks.backend.repository.CartRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.location.LocationDirectory;
import com.brainblocks.backend.service.location.LocationDirectory.Province;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Báo giá phí vận chuyển cho giỏ hàng hiện tại của khách. Chỉ báo giá, chưa cộng vào đơn hàng.
 */
@Service
@RequiredArgsConstructor
public class ShippingQuoteService {
    private final LocationDirectory locations;
    private final ShippingCalculator calculator;
    private final CartRepository cartRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public ShippingQuoteResponse quoteCurrentCart(Integer provinceCode) {
        Province province = locations.province(provinceCode)
                .orElseThrow(() -> new IllegalArgumentException("Province code is invalid"));
        // giỏ rỗng (hoặc chưa có giỏ) thì phí 0đ
        List<CartItem> cartItems = cartRepository.findWithItemsByCustomerId(currentUserProvider.getCurrentUserId())
                .map(Cart::getItems)
                .orElse(List.of());
        return toResponse(province, quote(province, cartItems));
    }

    /**
     * Phí vận chuyển các dòng giỏ hàng tới một tỉnh. Dùng chung cho API báo giá và lúc tạo đơn
     * (OrderService tự tính lại, không nhận phí từ client) nên hai nơi luôn ra cùng một số.
     */
    public ShippingQuote quote(Province province, List<CartItem> cartItems) {
        List<ShippingItem> items = cartItems.stream()
                .map(item -> ShippingItem.of(item.getProduct(), item.getQuantity()))
                .toList();
        return calculator.quote(province.region(), items);
    }

    private static ShippingQuoteResponse toResponse(Province province, ShippingQuote quote) {
        List<ParcelResponse> parcels = quote.parcels().stream()
                .map(p -> new ParcelResponse(
                        p.contents().stream()
                                .map(c -> new ParcelContentResponse(c.productId(), c.name(), c.quantity()))
                                .toList(),
                        p.actualWeightGrams(), p.volumetricWeightGrams(), p.chargeableWeightGrams(), p.oversized(),
                        p.baseFee()))
                .toList();
        return new ShippingQuoteResponse(province.code(), province.name(), quote.zone().name(), quote.zone().label(),
                quote.actualWeightGrams(), quote.volumetricWeightGrams(), quote.chargeableWeightGrams(),
                quote.parcelCount(), parcels, quote.baseFee(), quote.bulkySurcharge(), quote.totalFee(),
                quote.warnings());
    }
}
