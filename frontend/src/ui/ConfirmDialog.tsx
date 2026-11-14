import type { ReactNode } from 'react'
import { Button } from './Button'
import { Modal } from './Modal'

type Props = {
  open: boolean
  title: string
  message: ReactNode
  confirmLabel: string
  destructive?: boolean
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
  children?: ReactNode
}

/** Destructive actions always confirm. The confirm button is the one primary action. */
export function ConfirmDialog({ open, title, message, confirmLabel, destructive, busy, onConfirm, onCancel, children }: Props) {
  return (
    <Modal open={open} title={title} onClose={onCancel} description={message}
      actions={<>
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant={destructive ? 'danger' : 'primary'} loading={busy} onClick={onConfirm}>{confirmLabel}</Button>
      </>}>
      {children}
    </Modal>
  )
}
