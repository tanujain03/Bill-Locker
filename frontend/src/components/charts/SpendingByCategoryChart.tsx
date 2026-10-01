import { Bar, BarChart, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { CategorySpending } from '@/types';
import { formatCompactCurrency, formatCurrency } from '@/utils/format';
import { CHART_COLORS } from './chart-tokens';

interface SpendingByCategoryChartProps {
  data: CategorySpending[];
  currency: string;
}

const ROW_HEIGHT = 40;
const LABEL_WIDTH = 164;

/** Single-line category label (Recharts' default tick wraps long names). */
function CategoryTick({ x, y, payload }: { x?: number | string; y?: number | string; payload?: { value?: string } }) {
  const value = payload?.value ?? '';
  const label = value.length > 24 ? `${value.slice(0, 23)}…` : value;
  return (
    <text x={Number(x) - 8} y={Number(y)} dy={4} textAnchor="end" fill={CHART_COLORS.inkSecondary} fontSize={12}>
      <title>{value}</title>
      {label}
    </text>
  );
}

/**
 * Horizontal bars, one hue (a single series needs no legend — the card title
 * names it). Every bar carries its value at the tip, so no value axis is drawn.
 */
export function SpendingByCategoryChart({ data, currency }: SpendingByCategoryChartProps) {
  const sorted = [...data].sort((a, b) => b.amount - a.amount);
  const summary = sorted.map((item) => `${item.categoryName} ${formatCurrency(item.amount, currency)}`).join(', ');

  return (
    <div role="img" aria-label={`Spending by category: ${summary}`} style={{ height: sorted.length * ROW_HEIGHT + 8 }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={sorted} layout="vertical" margin={{ top: 0, right: 64, bottom: 0, left: 0 }} barCategoryGap={10}>
          <XAxis type="number" hide domain={[0, 'dataMax']} />
          <YAxis
            type="category"
            dataKey="categoryName"
            width={LABEL_WIDTH}
            tickLine={false}
            axisLine={{ stroke: CHART_COLORS.axis }}
            tick={CategoryTick}
          />
          <Tooltip
            cursor={{ fill: CHART_COLORS.cursor }}
            isAnimationActive={false}
            content={({ active, payload }) => {
              const item = active ? (payload?.[0]?.payload as CategorySpending | undefined) : undefined;
              if (!item) return null;
              return (
                <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 shadow-elevated">
                  <p className="text-sm font-semibold text-slate-900">{formatCurrency(item.amount, currency)}</p>
                  <p className="text-xs text-slate-500">{item.categoryName}</p>
                </div>
              );
            }}
          />
          <Bar
            dataKey="amount"
            fill={CHART_COLORS.series}
            radius={[0, 4, 4, 0]}
            maxBarSize={20}
            activeBar={{ fill: CHART_COLORS.seriesHover }}
            isAnimationActive={false}
          >
            <LabelList
              dataKey="amount"
              position="right"
              formatter={(value) => (typeof value === 'number' ? formatCompactCurrency(value, currency) : String(value ?? ''))}
              style={{ fill: CHART_COLORS.inkPrimary, fontSize: 12, fontWeight: 500 }}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Table twin of the chart (same data, for screen readers and exact values). */
export function SpendingTable({ data, currency }: SpendingByCategoryChartProps) {
  const sorted = [...data].sort((a, b) => b.amount - a.amount);
  const total = sorted.reduce((sum, item) => sum + item.amount, 0);
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
          <th scope="col" className="py-2 font-medium">
            Category
          </th>
          <th scope="col" className="py-2 text-right font-medium">
            Spent
          </th>
          <th scope="col" className="py-2 text-right font-medium">
            Share
          </th>
        </tr>
      </thead>
      <tbody className="tabular">
        {sorted.map((item) => (
          <tr key={item.categoryName} className="border-b border-slate-100 last:border-0">
            <td className="py-2 text-slate-700">{item.categoryName}</td>
            <td className="py-2 text-right font-medium text-slate-900">{formatCurrency(item.amount, currency)}</td>
            <td className="py-2 text-right text-slate-500">{total ? Math.round((item.amount / total) * 100) : 0}%</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
