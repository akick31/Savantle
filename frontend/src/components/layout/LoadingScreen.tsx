interface LoadingScreenProps {
  error?: string | null;
}

export default function LoadingScreen({ error }: LoadingScreenProps) {
  return (
    <div className="w-full flex-1 flex flex-col items-center justify-center px-4">
      <h1 className="text-2xl md:text-3xl font-pixel text-sv-accent mb-6">Savantle</h1>
      {error ? (
        <div className="text-center space-y-3">
          <p className="text-sv-red font-medium">Failed to load</p>
          <p className="text-sv-muted text-sm max-w-[280px]">{error}</p>
          <button
            onClick={() => window.location.reload()}
            className="mt-2 px-4 py-2 bg-sv-accent text-sv-bg rounded-lg text-sm font-semibold hover:opacity-90 transition-opacity"
          >
            Try again
          </button>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-2 border-sv-accent border-t-transparent rounded-full animate-spin" />
          <p className="text-sv-muted text-sm">Loading today's player…</p>
        </div>
      )}
    </div>
  );
}
