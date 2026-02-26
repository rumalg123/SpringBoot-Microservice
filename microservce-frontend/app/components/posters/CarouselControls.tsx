type Props = {
  count: number;
  index: number;
  onPrev: () => void;
  onNext: () => void;
  onSelect: (next: number) => void;
  compact: boolean;
};

export default function CarouselControls({ count, index, onPrev, onNext, onSelect, compact }: Props) {
  return (
    <>
      <button
        type="button"
        onClick={onPrev}
        aria-label="Previous poster"
        className={`absolute left-2.5 top-1/2 -translate-y-1/2 rounded-full border border-white/[0.22] bg-[rgba(7,10,18,0.55)] text-white grid place-items-center font-extrabold backdrop-blur-sm cursor-pointer z-[2] ${compact ? "w-[34px] h-[34px] text-[0.9rem]" : "w-10 h-10 text-lg"}`}
      >
        ‹
      </button>

      <button
        type="button"
        onClick={onNext}
        aria-label="Next poster"
        className={`absolute right-2.5 top-1/2 -translate-y-1/2 rounded-full border border-white/[0.22] bg-[rgba(7,10,18,0.55)] text-white grid place-items-center font-extrabold backdrop-blur-sm cursor-pointer z-[2] ${compact ? "w-[34px] h-[34px] text-[0.9rem]" : "w-10 h-10 text-lg"}`}
      >
        ›
      </button>

      <div
        aria-label="Poster slide indicators"
        className="absolute left-1/2 bottom-2.5 -translate-x-1/2 flex items-center gap-1.5 px-2 py-1.5 rounded-full bg-[rgba(7,10,18,0.45)] border border-white/[0.12] backdrop-blur-sm z-[2]"
      >
        {Array.from({ length: count }).map((_, dotIndex) => (
          <button
            key={dotIndex}
            type="button"
            onClick={() => onSelect(dotIndex)}
            aria-label={`Show poster ${dotIndex + 1}`}
            aria-pressed={dotIndex === index}
            className={`h-2 rounded-full border-none cursor-pointer transition-all duration-[140ms] ease-linear ${dotIndex === index ? "w-[18px] bg-brand/95" : "w-2 bg-white/45"}`}
          />
        ))}
      </div>
    </>
  );
}
