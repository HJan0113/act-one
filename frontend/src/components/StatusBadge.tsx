import type { ReactNode } from "react";

export type StatusTone = "active" | "danger" | "info" | "neutral" | "success" | "warning";

type StatusBadgeProps = {
  icon?: ReactNode;
  label: string;
  tone?: StatusTone;
};

export function StatusBadge({ icon, label, tone = "neutral" }: StatusBadgeProps) {
  return (
    <span className={`status-badge status-badge--${tone}`}>
      {icon ? <span className="status-badge__icon">{icon}</span> : null}
      <span>{label}</span>
    </span>
  );
}
