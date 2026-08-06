import { Component, type ReactNode } from "react";

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

/**
 * 错误边界组件
 * 捕获子组件的渲染错误，显示降级UI
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error("ErrorBoundary捕获到错误:", error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="flex h-full items-center justify-center p-8">
          <div className="text-center space-y-4">
            <div className="text-4xl">💫</div>
            <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">出错了</h2>
            <p className="text-sm text-[var(--color-muted-foreground)]">
              {this.state.error?.message ?? "发生了未知错误"}
            </p>
            <button
              onClick={() => this.setState({ hasError: false, error: null })}
              className="rounded-[var(--app-radius)] bg-[var(--color-primary)] px-4 py-2 text-sm text-[var(--color-primary-foreground)] hover:opacity-90"
            >
              重试
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
