import { useEffect, useState } from "react";
import { Alert, Button, Empty, Grid, Pagination, Skeleton, Table } from "antd";
import type { TableProps } from "antd";
import { request, requestBlob } from "../api";
import type { Page } from "../stock/types";

export function useRemote<T>(path: string, enabled = true) {
  const [data, setData] = useState<T>(),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true),
    [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!enabled) {
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    setError("");
    request<T>(path)
      .then((d) => {
        if (active) setData(d);
      })
      .catch((e) => {
        if (active) {
          setError((e as Error).message);
          setData(undefined);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [path, revision, enabled]);
  return { data, error, loading, reload: () => setRevision((r) => r + 1) };
}
export function LoadError({
  error,
  retry,
}: {
  error: string;
  retry: () => void;
}) {
  return error ? (
    <Alert
      className="form-alert"
      showIcon
      type="error"
      message={error}
      action={<Button onClick={retry}>重试加载</Button>}
    />
  ) : null;
}
export function Records<T extends { id?: number }>({
  data,
  columns,
  loading,
  page,
  size,
  onPage,
  card,
}: {
  data?: Page<T>;
  columns: TableProps<T>["columns"];
  loading: boolean;
  page: number;
  size: number;
  onPage: (page: number, size: number) => void;
  card: (row: T) => React.ReactNode;
}) {
  const screens = Grid.useBreakpoint();
  return (
    <>
      {screens.md ? (
        <Table<T>
          rowKey="id"
          dataSource={data?.records || []}
          columns={columns}
          loading={loading}
          pagination={false}
          scroll={{ x: 1000 }}
          locale={{ emptyText: <Empty description="暂无符合条件的记录" /> }}
        />
      ) : loading ? (
        <Skeleton active />
      ) : data?.records.length ? (
        <div className="operation-cards">
          {data.records.map((r, i) => (
            <article key={r.id || i}>{card(r)}</article>
          ))}
        </div>
      ) : (
        <Empty description="暂无符合条件的记录" />
      )}
      <div className="operation-pagination">
        <span>共 {data?.total || 0} 条</span>
        <Pagination
          current={page}
          pageSize={size}
          total={data?.total || 0}
          showSizeChanger
          pageSizeOptions={[10, 20, 50, 100]}
          onChange={onPage}
          size="small"
        />
      </div>
    </>
  );
}
export function AuthPhoto({ id }: { id: number }) {
  const [url, setUrl] = useState(""),
    [error, setError] = useState("");
  useEffect(() => {
    let active = true,
      address = "";
    requestBlob("/api/scraps/attachments/" + id)
      .then((blob) => {
        if (active) {
          address = URL.createObjectURL(blob);
          setUrl(address);
        }
      })
      .catch((e) => {
        if (active) setError((e as Error).message);
      });
    return () => {
      active = false;
      if (address) URL.revokeObjectURL(address);
    };
  }, [id]);
  return error ? (
    <Alert type="warning" message={error} />
  ) : url ? (
    <img className="scrap-photo" src={url} alt="仓库损耗照片" />
  ) : (
    <Skeleton.Image active />
  );
}
