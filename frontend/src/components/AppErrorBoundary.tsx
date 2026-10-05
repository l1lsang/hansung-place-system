import { Component, type ReactNode } from 'react'
export class AppErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() {
    return { failed: true }
  }
  render() {
    return this.state.failed ? (
      <div className="state" role="alert">
        <h1>화면을 불러오지 못했습니다.</h1>
        <p>페이지를 새로고침한 후 다시 시도해주세요.</p>
        <button className="btn primary" onClick={() => window.location.reload()}>
          새로고침
        </button>
      </div>
    ) : (
      this.props.children
    )
  }
}
