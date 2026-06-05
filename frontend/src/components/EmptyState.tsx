import type { ReactNode } from "react";

type EmptyStateProps = {
  action?: ReactNode;
  description: string;
  icon?: ReactNode;
  size?: "sm" | "md";
  title: string;
};

export function EmptyState({ action, description, icon, size = "md", title }: EmptyStateProps) {
  return (
    <div className={`empty-state empty-state--${size}`}>
      {icon ? <div className="empty-state__icon">{icon}</div> : null}
      <div>
        <p className="empty-state__title">{title}</p>
        <p className="empty-state__description">{description}</p>
      </div>
      {action ? <div className="empty-state__action">{action}</div> : null}
    </div>
  );
}
