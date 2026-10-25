// 契约要求预约和场地开放时间都是 30 分钟整点（秒必须为 0）。
// 接受 'HH:mm' 或 'HH:mm:ss'。
export function isHalfHourSharp(value: string): boolean {
  const match = /^(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value)
  if (!match) return false

  const minute = Number(match[2])
  const second = Number(match[3] ?? 0)

  return (minute === 0 || minute === 30) && second === 0
}
