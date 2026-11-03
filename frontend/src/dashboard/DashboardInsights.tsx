import { useState } from "react";
import { Button, Empty, Select, Skeleton, Table, Tag } from "antd";
import { Link } from "react-router-dom";
import Decimal from "decimal.js";
import dayjs from "dayjs";
import { money, quantity } from "../materials/types";
import { LoadError, useRemote } from "../operations/shared";
import "./dashboard.css";
interface Group {
  currency: string;
  unit: string;
  issueQuantity: string;
  returnQuantity: string;
  netQuantity: string;
  issueAmount: string;
  returnAmount: string;
  netAmount: string;
  scrapQuantity: string;
  scrapAmount: string;
}
interface MaterialTotal extends Group {
  materialId: number;
  materialCode: string;
  materialName: string;
  position: number;
}
interface Daily extends Group {
  day: string;
}
interface Ranking {
  userId: number;
  userName: string;
  issueQuantity: string;
  issueAmount: string;
  currency: string;
  unit: string;
  position: number;
}
interface Dashboard {
  scope: string;
  from: string;
  to: string;
  businessZone: string;
  canViewPeople: boolean;
  fundingDimension: string;
  stockValues: { currency: string; amount: string }[];
  lowStockCount: number;
  monthTotals: Group[];
  topMaterials: MaterialTotal[];
  trend: Daily[];
  funding: {
    currency: string;
    label: string;
    amount: string;
    percentage: string;
  }[];
  ranking: Ranking[];
  notes: string[];
}
const key = (g: { currency: string; unit: string }) =>
  g.currency + ":" + g.unit;
const scopeNames: Record<string, string> = {
  ALL: "全库统计",
  AGGREGATE: "授权汇总",
  PERSONAL: "仅本人领用",
  PUBLIC: "公开库存",
};
export function DashboardInsights() {
  const remote = useRemote<Dashboard>("/api/workspace/dashboard"),
    [selection, setSelection] = useState(""),
    d = remote.data,
    selected =
      d?.monthTotals.find((g) => key(g) === selection) || d?.monthTotals[0],
    groupKey = selected ? key(selected) : "";
  const top = d?.topMaterials.filter((g) => key(g) === groupKey) || [],
    trend = d?.trend.filter((g) => key(g) === groupKey) || [],
    rank = d?.ranking.filter((g) => key(g) === groupKey) || [],
    funds =
      d?.funding.filter(
        (g) =>
          g.currency === (selected?.currency || d.stockValues[0]?.currency),
      ) || [];
  const maxQuantity = top.reduce(
    (m, r) => Decimal.max(m, new Decimal(r.netQuantity).abs()),
    new Decimal(1),
  );
  return (
    <section className="dashboard-insights" aria-label="真实业务统计">
      <div className="section-heading">
        <h2>材料流转洞察</h2>
        {d && <Tag>{scopeNames[d.scope]}</Tag>}
      </div>
      <LoadError error={remote.error} retry={remote.reload} />
      {remote.loading ? (
        <section className="surface insights-loading">
          <Skeleton active paragraph={{ rows: 5 }} />
        </section>
      ) : (
        d && (
          <>
            <section className="surface insight-summary">
              <div>
                <span>当前库存总价值</span>
                {d.stockValues.length ? (
                  d.stockValues.map((v) => (
                    <strong key={v.currency}>
                      {money(quantity(v.amount), v.currency)}
                    </strong>
                  ))
                ) : (
                  <strong>暂无库存价值</strong>
                )}
                <small>按批次余额与入库单价 · 币种分别统计</small>
              </div>
              <div>
                <span>当前低库存</span>
                <strong>
                  {d.lowStockCount}
                  <small>种材料</small>
                </strong>
                <>
                  {d.scope === "PUBLIC" || d.scope === "PERSONAL" ? (
                    <small>库存数量低于安全阈值</small>
                  ) : (
                    <Link to="/alerts">查看预警处理</Link>
                  )}
                </>
              </div>
            </section>
            {d.scope !== "PUBLIC" && (
              <>
                <div className="insight-period">
                  <div>
                    <h3>
                      本月{d.scope === "PERSONAL" ? "我的" : ""}领用与消耗
                    </h3>
                    <p>
                      {d.from} 至 {d.to} · {d.businessZone}
                    </p>
                  </div>
                  {d.monthTotals.length > 0 && (
                    <Select
                      aria-label="仪表盘统计分组"
                      virtual={false}
                      value={groupKey}
                      options={d.monthTotals.map((g) => ({
                        value: key(g),
                        label: g.currency + " · " + g.unit,
                      }))}
                      onChange={setSelection}
                    />
                  )}
                </div>
                <section
                  className="surface monthly-totals"
                  aria-label="本月领用汇总"
                >
                  {selected ? (
                    <>
                      <div>
                        <span>本月领用量</span>
                        <strong>
                          {quantity(selected.issueQuantity)}{" "}
                          <small>{selected.unit}</small>
                        </strong>
                        <p>
                          归还 {quantity(selected.returnQuantity)} · 净消耗{" "}
                          {quantity(selected.netQuantity)}
                        </p>
                      </div>
                      <div>
                        <span>本月领用金额</span>
                        <strong>
                          {money(
                            quantity(selected.issueAmount),
                            selected.currency,
                          )}
                        </strong>
                        <p>
                          归还冲减{" "}
                          {money(
                            quantity(selected.returnAmount),
                            selected.currency,
                          )}
                        </p>
                      </div>
                      <div>
                        <span>本月净消耗金额</span>
                        <strong>
                          {money(
                            quantity(selected.netAmount),
                            selected.currency,
                          )}
                        </strong>
                        <p>
                          仓库报废独立计量：{quantity(selected.scrapQuantity)}{" "}
                          {selected.unit}
                        </p>
                      </div>
                    </>
                  ) : (
                    <div className="insight-empty-inline">
                      本月尚无领用或归还记录。完成真实流转后，统计将自动出现。
                    </div>
                  )}
                </section>
                <div className="insights-grid">
                  <section className="surface insight-panel">
                    <div className="panel-heading">
                      <h3>月度 TOP10 耗材</h3>
                      <span>按净消耗量</span>
                    </div>
                    {top.length ? (
                      <ol className="consumption-bars">
                        {top.map((r) => {
                          const negative = new Decimal(r.netQuantity).lt(0),
                            width = new Decimal(r.netQuantity)
                              .abs()
                              .div(maxQuantity)
                              .mul(50)
                              .toNumber();
                          return (
                            <li key={r.materialId + r.materialName}>
                              <div>
                                <span>
                                  {r.position}. {r.materialName}
                                </span>
                                <b>
                                  {quantity(r.netQuantity)} {r.unit}
                                </b>
                              </div>
                              <div className="signed-track">
                                <i
                                  className={negative ? "is-negative" : ""}
                                  style={{
                                    left: negative ? 50 - width + "%" : "50%",
                                    width: width + "%",
                                  }}
                                />
                              </div>
                              <small>{r.materialCode}</small>
                            </li>
                          );
                        })}
                      </ol>
                    ) : (
                      <Empty
                        description="暂无本月耗材记录"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                      />
                    )}
                  </section>
                  <section className="surface insight-panel">
                    <div className="panel-heading">
                      <h3>月度消耗趋势</h3>
                      <span>{selected?.currency || "按币种"} · 净消耗金额</span>
                    </div>
                    {trend.length ? (
                      <Trend
                        values={trend}
                        from={d.from}
                        to={d.to}
                        currency={selected!.currency}
                        unit={selected!.unit}
                      />
                    ) : (
                      <Empty
                        description="暂无可绘制的消耗记录"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                      />
                    )}
                  </section>
                  <section className="surface insight-panel">
                    <div className="panel-heading">
                      <h3>资金占比</h3>
                      <span>
                        {d.fundingDimension === "CATEGORY" ? "按分类" : "按项目"} · 领用金额，未扣归还 · {selected?.currency || "按币种"}
                      </span>
                    </div>
                    {funds.length ? (
                      <ul className="funding-bars">
                        {funds.map((f) => (
                          <li key={f.label}>
                            <div>
                              <strong>{f.label}</strong>
                              <span>{quantity(f.percentage)}%</span>
                            </div>
                            <progress
                              max={100}
                              value={Number(f.percentage)}
                              aria-label={f.label + "领用金额占比"}
                            />
                            <small>
                              {money(quantity(f.amount), f.currency)}
                            </small>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <Empty
                        description="暂无项目领用金额"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                      />
                    )}
                  </section>
                  {d.canViewPeople && (
                    <section className="surface insight-panel">
                      <div className="panel-heading">
                        <h3>个人领用排行榜</h3>
                        <span>本月 · 按领用量</span>
                      </div>
                      <Table<Ranking>
                        rowKey="userId"
                        size="small"
                        pagination={false}
                        dataSource={rank}
                        scroll={{ x: 340 }}
                        locale={{
                          emptyText: (
                            <Empty
                              description="暂无领用排行"
                              image={Empty.PRESENTED_IMAGE_SIMPLE}
                            />
                          ),
                        }}
                        columns={[
                          { title: "排名", dataIndex: "position", width: 55 },
                          { title: "使用人", dataIndex: "userName" },
                          {
                            title: "领用量",
                            render: (_, r) =>
                              quantity(r.issueQuantity) + " " + r.unit,
                          },
                          {
                            title: "金额",
                            render: (_, r) =>
                              money(quantity(r.issueAmount), r.currency),
                          },
                        ]}
                      />
                    </section>
                  )}
                </div>
                <div className="insights-notes">
                  <p>{d.notes.join(" ")}</p>
                  <Link to="/reports">
                    <Button type="link">查看完整报表</Button>
                  </Link>
                </div>
              </>
            )}
            {d.scope === "PUBLIC" && (
              <div className="operation-note">
                当前账户可查看公开库存；领用金额与个人排行按角色权限提供。
              </div>
            )}
          </>
        )
      )}
    </section>
  );
}
function Trend({
  values,
  from,
  to,
  currency,
  unit,
}: {
  values: Daily[];
  from: string;
  to: string;
  currency: string;
  unit: string;
}) {
  const lookup = new Map(values.map((r) => [r.day, r])),
    days = Array.from(
      { length: dayjs(to).diff(dayjs(from), "day") + 1 },
      (_, i) => {
        const day = dayjs(from).add(i, "day").format("YYYY-MM-DD");
        return {
          day,
          netAmount: lookup.get(day)?.netAmount || "0",
          netQuantity: lookup.get(day)?.netQuantity || "0",
        };
      },
    ),
    max = days.reduce(
      (m, r) => Decimal.max(m, new Decimal(r.netAmount).abs()),
      new Decimal("0.0001"),
    ),
    points = days
      .map(
        (r, i) =>
          `${35 + (i * 590) / Math.max(1, days.length - 1)},${98 - new Decimal(r.netAmount).div(max).mul(60).toNumber()}`,
      )
      .join(" ");
  return (
    <>
      <svg
        viewBox="0 0 660 200"
        role="img"
        aria-label="本月净消耗金额趋势"
        className="consumption-trend"
      >
        <title>
          按实际领用与归还日期统计，负值表示归还冲减，单位 {currency}
        </title>
        <line x1="35" x2="625" y1="98" y2="98" className="trend-zero" />
        <polyline points={points} fill="none" className="trend-line" />
        {days.map((r, i) => (
          <circle
            key={r.day}
            cx={35 + (i * 590) / Math.max(1, days.length - 1)}
            cy={98 - new Decimal(r.netAmount).div(max).mul(60).toNumber()}
            r="3"
          >
            <title>
              {r.day} · {money(quantity(r.netAmount), currency)}
            </title>
          </circle>
        ))}
        <text x="35" y="185">
          {from.slice(5)}
        </text>
        <text x="625" y="185" textAnchor="end">
          {to.slice(5)}
        </text>
        <text x="10" y="102">
          0
        </text>
      </svg>
      <details className="trend-details">
        <summary>查看每日明细</summary>
        <Table
          rowKey="day"
          size="small"
          pagination={false}
          dataSource={days}
          scroll={{ x: 320 }}
          columns={[
            { title: "日期", dataIndex: "day" },
            {
              title: "净消耗量",
              render: (_, r) => quantity(r.netQuantity) + " " + unit,
            },
            {
              title: "净消耗金额",
              render: (_, r) => money(quantity(r.netAmount), currency),
            },
          ]}
        />
      </details>
    </>
  );
}
