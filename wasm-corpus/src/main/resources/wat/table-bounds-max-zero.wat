;; A table whose maximum is 0.
(module
  (table 0 0 funcref)

  (func (export "grow") (param i32) (result i32)
    (table.grow 0 (ref.null func) (local.get 0)))
)
