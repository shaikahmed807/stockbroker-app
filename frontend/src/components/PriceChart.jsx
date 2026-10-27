import { Area, AreaChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { price, time } from '../utils/format';

// SVG presentation attributes can't read CSS variables reliably, so chart colors are literal.
const C = { up: '#1F8A5B', down: '#C8403A', rule: '#DDE3EA', muted: '#66758A' };

function ChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const p = payload[0].payload;
  return (
    <div className="chart-tip">
      <strong>₹{price(p.price)}</strong>
      <span>{time(p.timestamp)}</span>
    </div>
  );
}

export default function PriceChart({ points, previousClose, up }) {
  const color = up ? C.up : C.down;
  const prices = points.map((p) => p.price);
  const min = Math.min(...prices, previousClose ?? Infinity);
  const max = Math.max(...prices, previousClose ?? -Infinity);
  const pad = (max - min) * 0.12 || 1;

  return (
    <ResponsiveContainer width="100%" height={320}>
      <AreaChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id="fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity={0.18} />
            <stop offset="100%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke={C.rule} vertical={false} />
        <XAxis
          dataKey="timestamp"
          tickFormatter={time}
          minTickGap={60}
          tick={{ fill: C.muted, fontSize: 12 }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis
          domain={[min - pad, max + pad]}
          tickFormatter={(v) => price(v)}
          width={78}
          orientation="right"
          tick={{ fill: C.muted, fontSize: 12 }}
          axisLine={false}
          tickLine={false}
        />
        {previousClose ? (
          <ReferenceLine
            y={previousClose}
            stroke={C.muted}
            strokeDasharray="4 4"
            label={{ value: 'Prev close', position: 'insideTopLeft', fill: C.muted, fontSize: 11 }}
          />
        ) : null}
        <Tooltip content={<ChartTooltip />} cursor={{ stroke: C.muted, strokeDasharray: '3 3' }} />
        <Area
          type="monotone"
          dataKey="price"
          stroke={color}
          strokeWidth={2}
          fill="url(#fill)"
          isAnimationActive={false}
          dot={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
