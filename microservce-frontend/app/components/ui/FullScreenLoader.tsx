type Props = {
  message?: string;
};

export default function FullScreenLoader({
  message = "Loading...",
}: Props) {
  return (
    <div className="grid min-h-screen place-items-center" style={{ background: "var(--bg)" }}>
      <div className="text-center">
        <div className="spinner-lg" />
        <p style={{ marginTop: 16, color: "var(--muted)", fontSize: "0.875rem" }}>
          {message}
        </p>
      </div>
    </div>
  );
}
