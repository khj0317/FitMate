import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AppLayout } from './components/AppLayout'
import { Spinner } from './components/ui'
import { AdminPage } from './pages/AdminPage'
import { LoginPage, SignupPage } from './pages/AuthPages'
import { ChatsPage } from './pages/ChatsPage'
import { CommunityPage } from './pages/CommunityPage'
import { DiscoverPage } from './pages/DiscoverPage'
import { FindAccountPage } from './pages/FindAccountPage'
import { GatheringDetailPage } from './pages/GatheringDetailPage'
import { GatheringsPage } from './pages/GatheringsPage'
import { PostDetailPage } from './pages/PostDetailPage'
import { ProfilePage } from './pages/ProfilePage'
import { RequestsPage } from './pages/RequestsPage'
import { UserProfilePage } from './pages/UserProfilePage'
import { useAuth } from './providers/AuthProvider'
import { ChatSocketProvider } from './providers/ChatSocketProvider'

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
    </BrowserRouter>
  )
}
