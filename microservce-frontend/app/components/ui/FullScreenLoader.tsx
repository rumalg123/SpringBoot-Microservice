type Props = {
  message?: string;
};

export default function FullScreenLoader({
  message = "Loading...",
}: Props) {
  return (
    <div className="grid min-h-screen place-items-center bg-bg">
      <div className="text-center">
        <div className="spinner-lg" />
        <p className="mt-4 text-muted text-base">
          {message}
        </p>
      </div>
    </div>
  );
}
