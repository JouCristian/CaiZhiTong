import { useState } from 'react'
import { Alert, App, Button, Form, InputNumber, Modal } from 'antd'
import { request } from '../api'
import type { Material } from '../materials/types'
export function QuotaEditor({
  material,
  onClose,
  onSaved,
}: {
  material: Material
  onClose: () => void
  onSaved: (m: Material) => void
}) {
  const [form] = Form.useForm(),
    [saving, setSaving] = useState(false),
    [error, setError] = useState('')
  const { message } = App.useApp()
  async function save(values: { monthlyIssueLimit?: string | null }) {
    setSaving(true)
    setError('')
    try {
      const m = await request<Material>(
        '/api/materials/' + material.id + '/issue-limit',
        {
          method: 'PATCH',
          body: JSON.stringify({
            monthlyIssueLimit: values.monthlyIssueLimit || null,
          }),
        },
      )
      void message.success('个人月度领用限额已更新')
      onSaved(m)
      onClose()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setSaving(false)
    }
  }
  return (
    <Modal
      title="个人月度领用限额"
      open
      onCancel={onClose}
      maskClosable={!saving}
      footer={
        <>
          <Button disabled={saving} onClick={onClose}>
            取消
          </Button>
          <Button type="primary" loading={saving} onClick={() => form.submit()}>
            保存限额
          </Button>
        </>
      }
    >
      <p className="stock-hint">
        {material.name} ·
        按每位用户、每个自然月累计，所有批次共享额度。修改限额不会清除本月已领数量。
      </p>
      {error && <Alert type="error" message={error} />}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ monthlyIssueLimit: material.monthlyIssueLimit }}
        onFinish={save}
        disabled={saving}
        style={{ marginTop: 20 }}
      >
        <Form.Item
          label="每人每月限额"
          name="monthlyIssueLimit"
          extra="留空表示不限制，填写时必须大于零。"
          rules={[
            {
              validator: async (_, v) => {
                if (v !== undefined && v !== null && Number(v) <= 0)
                  throw new Error('限额必须大于零，或留空表示不限')
              },
            },
          ]}
        >
          <InputNumber
            stringMode
            min="0.001"
            max="999999999999999.999"
            precision={3}
            addonAfter={material.unit}
            style={{ width: '100%' }}
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}
