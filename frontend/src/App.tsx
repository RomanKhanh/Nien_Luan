import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import { RequireAuth } from '@/auth/RequireAuth'
import { ShopLayout } from '@/components/layout/ShopLayout'
import { PageLoader } from '@/components/ui/States'
import HomePage from '@/pages/HomePage'
import NotFoundPage from '@/pages/NotFoundPage'

// tách bundle theo trang để trang chủ tải nhanh (yêu cầu < 3 giây); trang admin chỉ tải khi vào /admin
const ProductListPage = lazy(() => import('@/pages/ProductListPage'))
const ProductDetailPage = lazy(() => import('@/pages/ProductDetailPage'))
const LoginPage = lazy(() => import('@/pages/auth/LoginPage'))
const RegisterPage = lazy(() => import('@/pages/auth/RegisterPage'))
const ForgotPasswordPage = lazy(() => import('@/pages/auth/ForgotPasswordPage'))
const ResetPasswordPage = lazy(() => import('@/pages/auth/ResetPasswordPage'))
const AccountPage = lazy(() => import('@/pages/account/AccountPage'))
const ChildrenPage = lazy(() => import('@/pages/children/ChildrenPage'))
const SkillProfilePage = lazy(() => import('@/pages/children/SkillProfilePage'))
const CartPage = lazy(() => import('@/pages/cart/CartPage'))
const CheckoutPage = lazy(() => import('@/pages/cart/CheckoutPage'))
const OrdersPage = lazy(() => import('@/pages/orders/OrdersPage'))
const OrderDetailPage = lazy(() => import('@/pages/orders/OrderDetailPage'))
const FeedbackPage = lazy(() => import('@/pages/account/FeedbackPage'))
const NotificationsPage = lazy(() => import('@/pages/account/NotificationsPage'))
const ChatPage = lazy(() => import('@/pages/chat/ChatPage'))
const MomoReturnPage = lazy(() => import('@/pages/payment/MomoReturnPage'))

const AdminLayout = lazy(() => import('@/pages/admin/AdminLayout'))
const AdminDashboardPage = lazy(() => import('@/pages/admin/DashboardPage'))
const AdminProductsPage = lazy(() => import('@/pages/admin/ProductsPage'))
const AdminCatalogPage = lazy(() => import('@/pages/admin/CatalogPage'))
const AdminOrdersPage = lazy(() => import('@/pages/admin/OrdersPage'))
const AdminUsersPage = lazy(() => import('@/pages/admin/UsersPage'))
const AdminReviewsPage = lazy(() => import('@/pages/admin/ReviewsPage'))
const AdminComplaintsPage = lazy(() => import('@/pages/admin/ComplaintsPage'))
const AdminChatbotPage = lazy(() => import('@/pages/admin/ChatbotPage'))
const AdminKnowledgePage = lazy(() => import('@/pages/admin/KnowledgePage'))

export default function App() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />

        <Route element={<ShopLayout />}>
          <Route index element={<HomePage />} />
          <Route path="products" element={<ProductListPage />} />
          <Route path="products/:id" element={<ProductDetailPage />} />
          <Route path="cart" element={<CartPage />} />
          <Route path="chat" element={<ChatPage />} />
          <Route path="payment/momo-return" element={<MomoReturnPage />} />
          <Route
            path="account"
            element={
              <RequireAuth>
                <AccountPage />
              </RequireAuth>
            }
          />
          <Route
            path="children"
            element={
              <RequireAuth role="CUSTOMER">
                <ChildrenPage />
              </RequireAuth>
            }
          />
          <Route
            path="children/:id"
            element={
              <RequireAuth role="CUSTOMER">
                <SkillProfilePage />
              </RequireAuth>
            }
          />
          <Route
            path="checkout"
            element={
              <RequireAuth role="CUSTOMER">
                <CheckoutPage />
              </RequireAuth>
            }
          />
          <Route
            path="orders"
            element={
              <RequireAuth role="CUSTOMER">
                <OrdersPage />
              </RequireAuth>
            }
          />
          <Route
            path="orders/:id"
            element={
              <RequireAuth role="CUSTOMER">
                <OrderDetailPage />
              </RequireAuth>
            }
          />
          <Route
            path="notifications"
            element={
              <RequireAuth role="CUSTOMER">
                <NotificationsPage />
              </RequireAuth>
            }
          />
          <Route
            path="feedback"
            element={
              <RequireAuth role="CUSTOMER">
                <FeedbackPage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        <Route
          path="/admin"
          element={
            <RequireAuth role="ADMIN">
              <AdminLayout />
            </RequireAuth>
          }
        >
          <Route index element={<AdminDashboardPage />} />
          <Route path="products" element={<AdminProductsPage />} />
          <Route path="catalog" element={<AdminCatalogPage />} />
          <Route path="orders" element={<AdminOrdersPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="reviews" element={<AdminReviewsPage />} />
          <Route path="complaints" element={<AdminComplaintsPage />} />
          <Route path="chatbot" element={<AdminChatbotPage />} />
          <Route path="knowledge" element={<AdminKnowledgePage />} />
        </Route>
      </Routes>
    </Suspense>
  )
}
