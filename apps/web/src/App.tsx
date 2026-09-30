import { lazy, Suspense, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AppLayout } from './components/AppLayout'
import { PageLoader, Spinner } from './components/ui'
import { useAuth } from './providers/AuthProvider'
import { ChatSocketProvider } from './providers/ChatSocketProvider'

// 화면마다 코드를 나눠서 처음에는 지금 보는 화면만 받는다 (모든 화면을 한 파일로 받으면 첫 로딩이 느림)
const LoginPage = lazy(() => import('./pages/AuthPages').then((m) => ({ default: m.LoginPage })))
const SignupPage = lazy(() => import('./pages/AuthPages').then((m) => ({ default: m.SignupPage })))
const FindAccountPage = lazy(() => import('./pages/FindAccountPage').then((m) => ({ default: m.FindAccountPage })))
const DiscoverPage = lazy(() => import('./pages/DiscoverPage').then((m) => ({ default: m.DiscoverPage })))
const GatheringsPage = lazy(() => import('./pages/GatheringsPage').then((m) => ({ default: m.GatheringsPage })))
const GatheringDetailPage = lazy(() => import('./pages/GatheringDetailPage').then((m) => ({ default: m.GatheringDetailPage })))
const CommunityPage = lazy(() => import('./pages/CommunityPage').then((m) => ({ default: m.CommunityPage })))
const PostDetailPage = lazy(() => import('./pages/PostDetailPage').then((m) => ({ default: m.PostDetailPage })))
const UserProfilePage = lazy(() => import('./pages/UserProfilePage').then((m) => ({ default: m.UserProfilePage })))
const AdminPage = lazy(() => import('./pages/AdminPage').then((m) => ({ default: m.AdminPage })))
const RequestsPage = lazy(() => import('./pages/RequestsPage').then((m) => ({ default: m.RequestsPage })))
const ChatsPage = lazy(() => import('./pages/ChatsPage').then((m) => ({ default: m.ChatsPage })))
const ProfilePage = lazy(() => import('./pages/ProfilePage').then((m) => ({ default: m.ProfilePage })))

function RequireAuth({ children }: { children: ReactNode }) {
  const { loggedIn } = useAuth()
  return loggedIn ? children : <Navigate to="/login" replace />
}

function GuestOnly({ children }: { children: ReactNode }) {
  const { loggedIn, afterLoginPath } = useAuth()
  return loggedIn ? <Navigate to={afterLoginPath} replace /> : children
}

export function App() {
  const { ready } = useAuth()
  // 새로고침 직후 쿠키로 로그인 상태를 확인하는 동안은 로그인 화면으로 튕기지 않게 기다린다
  if (!ready) {
    return (
      <div className="flex min-h-dvh items-center justify-center">
        <Spinner className="size-8" />
      </div>
    )
  }
  return (
    <BrowserRouter>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
          <Route path="/signup" element={<GuestOnly><SignupPage /></GuestOnly>} />
          <Route path="/find-account" element={<GuestOnly><FindAccountPage /></GuestOnly>} />
          <Route
            element={
              <RequireAuth>
                <ChatSocketProvider>
                  <AppLayout />
                </ChatSocketProvider>
              </RequireAuth>
            }
          >
            <Route index element={<DiscoverPage />} />
            <Route path="gatherings" element={<GatheringsPage />} />
            <Route path="gatherings/:gatheringId" element={<GatheringDetailPage />} />
            <Route path="community" element={<CommunityPage />} />
            <Route path="community/:postId" element={<PostDetailPage />} />
            <Route path="users/:userId" element={<UserProfilePage />} />
            <Route path="admin" element={<AdminPage />} />
            <Route path="requests" element={<RequestsPage />} />
            <Route path="chats" element={<ChatsPage />} />
            <Route path="chats/:roomId" element={<ChatsPage />} />
            <Route path="profile" element={<ProfilePage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}
