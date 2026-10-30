;; Puts a function whose declared type is a subtype in its table.
(module
  (type $super (sub (func (result funcref))))
  (type $sub (sub $super (func (result (ref func)))))
  (table (export "tab") 1 funcref)

  (func $f (type $sub) (ref.func $f))

  (elem (i32.const 0) $f)
)
