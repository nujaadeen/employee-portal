import { Navigate, Route, Routes } from 'react-router-dom'
import { AccessPage } from '@/features/access/AccessPage'
import { AccountsPage } from '@/features/accounts/AccountsPage'
import { AiPage } from '@/features/ai/AiPage'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { EmployeesPage } from '@/features/employees/EmployeesPage'
import { LeavePage } from '@/features/leave/LeavePage'
import { TaskDetailPage } from '@/features/tasks/TaskDetailPage'
import { TasksPage } from '@/features/tasks/TasksPage'
import { Layout } from '@/layout/Layout'
import { RequireAnyPermission, RequirePermission } from '@/layout/RequirePermission'
import { NotFound } from '@/pages/StatusPages'
import { ProfilePage } from '@/pages/ProfilePage'
import { Styleguide } from '@/pages/Styleguide'

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="leave" element={<RequirePermission permission="leave:request"><LeavePage /></RequirePermission>} />
        <Route path="employees" element={<RequirePermission permission="employee:directory"><EmployeesPage /></RequirePermission>} />
        <Route path="tasks" element={<RequirePermission permission="task:view"><TasksPage /></RequirePermission>} />
        <Route path="tasks/:key" element={<RequirePermission permission="task:view"><TaskDetailPage /></RequirePermission>} />
        <Route path="ai" element={<RequirePermission permission="ai:chat"><AiPage /></RequirePermission>} />
        <Route path="ai/:id" element={<RequirePermission permission="ai:chat"><AiPage /></RequirePermission>} />
        <Route path="accounts" element={<RequireAnyPermission permissions={['social:publish:company', 'social:publish:personal']}><AccountsPage /></RequireAnyPermission>} />
        <Route path="access" element={<RequirePermission permission="access:manage"><AccessPage /></RequirePermission>} />
        <Route path="profile" element={<ProfilePage />} />
        {import.meta.env.DEV ? <Route path="styleguide" element={<Styleguide />} /> : null}
        <Route path="callback" element={<Navigate to="/" replace />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
