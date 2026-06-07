import type { ButtonHTMLAttributes, ReactNode } from "react";

type ActionButtonVariant = "primary" | "secondary" | "ghost" | "quiet";
type ActionButtonSize = "sm" | "md";

type ActionButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  icon?: ReactNode;
  iconOnly?: boolean;
  size?: ActionButtonSize;
  variant?: ActionButtonVariant;
};

export function ActionButton({
  children,
  className = "",
  icon,
  iconOnly = false,
  size = "md",
  type = "button",
  variant = "secondary",
  ...buttonProps
}: ActionButtonProps) {
  const classNames = [
    "action-button",
    `action-button--${variant}`,
    `action-button--${size}`,
    iconOnly ? "action-button--icon-only" : "",
    className
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button className={classNames} type={type} {...buttonProps}>
      {icon ? <span className="action-button__icon">{icon}</span> : null}
      {children ? <span className="action-button__label">{children}</span> : null}
    </button>
  );
}
